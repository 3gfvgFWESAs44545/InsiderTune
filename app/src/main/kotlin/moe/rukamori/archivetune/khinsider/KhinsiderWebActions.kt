/*
 * Pixel Notes — optional khinsider account
 * GPL-3.0 License
 *
 * Performs account actions the way a person would: loads the album page in an invisible,
 * signed-in WebView and presses khinsider's own favourite button
 * (<div class="albumFavorite"><i>favorite_border|favorite</i><div class="albumFavoriteText">…</div></div>).
 * Several ways of pressing it are tried in turn; the flow stops as soon as the heart changes
 * or the site sends a request.
 */

package moe.rukamori.archivetune.khinsider

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.innertube.khinsider.Khinsider
import org.json.JSONObject
import org.json.JSONTokener
import timber.log.Timber
import java.io.IOException
import kotlin.coroutines.resume

object KhinsiderWebActions {
    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())

    private const val PAGE_TIMEOUT_MS = 30_000L
    private const val SETTLE_MS = 1_000L
    private const val STEP_WAIT_MS = 3_000L
    private const val XHR_EXTRA_WAIT_MS = 3_000L
    private val STRATEGIES = listOf("native", "mouse", "touch", "text", "jquery")
    private val FLOW_TIMEOUT_MS = PAGE_TIMEOUT_MS + STRATEGIES.size * (STEP_WAIT_MS + XHR_EXTRA_WAIT_MS) + 10_000

    private data class FlowResult(
        val outcome: String,
        val log: String,
    )

    /** Adds/removes an album from khinsider favourites. Throws if the button can't be found or used. */
    suspend fun setFavorite(
        context: Context,
        albumSlug: String,
        favorite: Boolean,
    ) = mutex.withLock {
        // Pixel Notes: a hidden web view loads the album page; its bytes can't be measured, only counted.
        moe.rukamori.archivetune.innertube.khinsider.KhinsiderTraffic.recordNetwork(
            moe.rukamori.archivetune.innertube.khinsider.TrafficCategory.WEB_VIEW,
            0,
            (if (favorite) "like · " else "unlike · ") + albumSlug,
        )
        val url = "${Khinsider.BASE_URL}/game-soundtracks/album/$albumSlug"
        val result =
            withTimeout(FLOW_TIMEOUT_MS + 5_000) {
                withContext(Dispatchers.Main) { runFavoriteFlow(context.applicationContext, url, favorite, diagnostic = false) }
            }
        Timber.tag("KhinsiderWeb").d("favourite %s=%s -> %s", albumSlug, favorite, result.outcome)
        val outcome = result.outcome
        when {
            outcome == "already" || outcome == "sent" || outcome.startsWith("done") -> Unit
            outcome == "noid" -> throw IOException("Couldn't find the album's khinsider ID")
            outcome.startsWith("http") -> throw IOException("khinsider refused the request (${outcome.removePrefix("http")})")
            outcome == "none" -> throw IOException("Couldn't find khinsider's favourite button")
            outcome == "signed_out" -> throw IOException("Signed out of khinsider")
            outcome == "mismatch" -> throw IOException("khinsider didn't accept the change")
            else -> throw IOException("khinsider favourite failed ($outcome)")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun runFavoriteFlow(
        context: Context,
        url: String,
        want: Boolean,
        diagnostic: Boolean,
    ): FlowResult =
        suspendCancellableCoroutine { cont ->
            val webView = WebView(context)
            var finished = false
            var started = false

            fun eval(
                script: String,
                callback: (String) -> Unit,
            ) {
                if (finished) return
                webView.evaluateJavascript(script) { raw ->
                    callback(runCatching { JSONTokener(raw).nextValue()?.toString() }.getOrNull() ?: "null")
                }
            }

            fun finish(outcome: String) {
                if (finished) return
                // Grab the in-page log before tearing down.
                webView.evaluateJavascript("(window.__vgLog||[]).join('\\n')") { raw ->
                    val log = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
                    if (finished) return@evaluateJavascript
                    finished = true
                    webView.stopLoading()
                    webView.destroy()
                    if (cont.isActive) cont.resume(FlowResult(outcome, log))
                }
                // Safety net if the page can't answer.
                mainHandler.postDelayed({
                    if (finished) return@postDelayed
                    finished = true
                    webView.destroy()
                    if (cont.isActive) cont.resume(FlowResult(outcome, "(no log)"))
                }, 1_500)
            }

            fun tryStrategy(index: Int) {
                if (finished) return
                if (index >= STRATEGIES.size) {
                    finish("mismatch")
                    return
                }
                val name = STRATEGIES[index]
                eval("window.__vgPress('$name')") {
                    mainHandler.postDelayed({
                        eval("JSON.stringify({s:window.__vgState(),x:window.__vgXhr||0})") { raw ->
                            val json = runCatching { JSONObject(raw) }.getOrNull()
                            val state = json?.optString("s").orEmpty()
                            val xhr = json?.optInt("x") ?: 0
                            when {
                                state == (if (want) "fav" else "notfav") -> finish("done:$name")
                                xhr > 0 -> {
                                    // The site sent something: give it time, then stop (avoid toggling twice).
                                    mainHandler.postDelayed({
                                        eval("window.__vgState()") { later ->
                                            finish(if (later == (if (want) "fav" else "notfav")) "done:$name" else "mismatch")
                                        }
                                    }, XHR_EXTRA_WAIT_MS)
                                }
                                else -> tryStrategy(index + 1)
                            }
                        }
                    }, STEP_WAIT_MS)
                }
            }

            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.webChromeClient =
                object : WebChromeClient() {
                    override fun onJsConfirm(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: JsResult,
                    ): Boolean {
                        view?.evaluateJavascript("(window.__vgLog=window.__vgLog||[]).push('confirm: '+${JSONObject.quote(message ?: "")})", null)
                        result.confirm()
                        return true
                    }

                    override fun onJsAlert(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: JsResult,
                    ): Boolean {
                        view?.evaluateJavascript("(window.__vgLog=window.__vgLog||[]).push('alert: '+${JSONObject.quote(message ?: "")})", null)
                        result.confirm()
                        return true
                    }
                }
            webView.webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(
                        view: WebView,
                        url: String?,
                    ) {
                        if (started) return
                        started = true
                        mainHandler.postDelayed({
                            eval(setupScript(diagnostic)) { state ->
                                when (state) {
                                    "signed_out", "none" -> finish(state)
                                    (if (want) "fav" else "notfav") -> finish("already")
                                    // khinsider's own click handler never gets attached in a WebView (its
                                    // script stops at navigator.mediaSession), so send the request the
                                    // button would send: GET /cp/album_favorite_toggle?albumid=<id>.
                                    else -> eval(directToggleScript(want)) { result -> finish(result) }
                                }
                            }
                        }, SETTLE_MS)
                    }
                }
            cont.invokeOnCancellation { mainHandler.post { finish("cancelled") } }
            mainHandler.postDelayed({ finish("timeout") }, FLOW_TIMEOUT_MS)
            webView.loadUrl(url)
        }

    /**
     * Reads the album ID from khinsider's (packed) page script, sends the favourite toggle request
     * the site's button uses, then reloads the page HTML to confirm the new state.
     * Returns done / mismatch / sent / noid / httpNNN.
     */
    private fun directToggleScript(want: Boolean): String =
        """
        (function(want){
          var log=window.__vgLog=window.__vgLog||[];
          var id=null;
          [].forEach.call(document.scripts,function(s){
            if(id) return;
            var t=(s.textContent||'').trim(); if(!t) return;
            var code=t;
            if(/^eval\(function\(p,a,c,k,e,[rd]\)/.test(t)){ try{ code=new Function('return '+t.slice(4))(); }catch(e){ return; } }
            var m=/album_favorite_toggle['"]\s*,\s*\{\s*albumid\s*:\s*(\d+)/.exec(code)||/var\s+albumid\s*=\s*(\d+)/.exec(code);
            if(m) id=m[1];
          });
          log.push('album id: '+id);
          if(!id) return 'noid';
          var x=new XMLHttpRequest();
          x.open('GET','/cp/album_favorite_toggle?albumid='+encodeURIComponent(id),false);
          x.setRequestHeader('X-Requested-With','XMLHttpRequest');
          x.send();
          log.push('toggle: HTTP '+x.status+' '+String(x.responseText||'').slice(0,200));
          if(x.status<200||x.status>=400) return 'http'+x.status;
          var y=new XMLHttpRequest();
          y.open('GET',location.pathname+'?_vg='+Date.now(),false);
          y.send();
          var m2=/class="albumFavorite([^"]*)"[^>]*>\s*<i[^>]*>\s*([a-z_]+)\s*<\/i>/.exec(y.responseText||'');
          log.push('after reload: '+(m2?('class='+m2[1].trim()+' icon='+m2[2]):'button not found'));
          if(!m2) return 'sent';
          var nowFav=/favoriteAdded/.test(m2[1])||m2[2]==='favorite';
          return nowFav===want?'done':'mismatch';
        })($want)
        """.trimIndent()

    /** Installs helpers (__vgState, __vgPress, request hooks, log) and returns the current state. */
    private fun setupScript(diagnostic: Boolean): String =
        """
        (function(diag){
          var log=window.__vgLog=window.__vgLog||[];
          function clip(s,n){s=String(s==null?'':s);return s.length>n?s.slice(0,n)+'…':s;}
          if (document.querySelector('input[type=password]') && !document.querySelector('a[href*=logout]')) return 'signed_out';
          function btn(){return document.querySelector('#pageContent .albumFavorite')||document.querySelector('.albumFavorite');}
          window.__vgState=function(){
            var b=btn(); if(!b) return 'none';
            var icon=((b.querySelector('i')||{}).innerText||'').trim().toLowerCase();
            var text=((b.querySelector('.albumFavoriteText')||b).innerText||'').trim().toLowerCase();
            var fav=b.classList.contains('favoriteAdded')||icon==='favorite'||(/added|remove/.test(text)&&!/add to/.test(text));
            return fav?'fav':'notfav';
          };
          window.__vgXhr=0;
          var XO=XMLHttpRequest.prototype.open, XS=XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open=function(m,u){this.__vg=m+' '+u; return XO.apply(this,arguments);};
          XMLHttpRequest.prototype.send=function(body){
            var x=this; var u=String(x.__vg||'');
            if(!/google|analytics|cloudflare/.test(u)){window.__vgXhr++; log.push('xhr '+u+' body='+clip(body,300));
              x.addEventListener('loadend',function(){log.push('xhr done '+x.status+' '+clip(x.responseText,500));});}
            return XS.apply(this,arguments);
          };
          if(window.fetch){var F=window.fetch; window.fetch=function(u,o){var su=String(u&&u.url||u);
            if(!/google|analytics|cloudflare/.test(su)){window.__vgXhr++; log.push('fetch '+su+' '+clip(o&&o.body,300));}
            return F.apply(this,arguments);};}
          window.addEventListener('error',function(e){log.push('js error: '+clip(e.message,200));});
          window.__vgPress=function(how){
            var b=btn(); if(!b) return 'none';
            log.push('press: '+how);
            function fire(el,type,ctor){try{el.dispatchEvent(new ctor(type,{bubbles:true,cancelable:true,view:window}));}catch(e){log.push('event '+type+' failed: '+e.message);}}
            if(how==='native'){ b.click(); }
            else if(how==='mouse'){ ['pointerdown','mousedown','pointerup','mouseup'].forEach(function(t){fire(b,t,t.indexOf('pointer')===0&&window.PointerEvent?PointerEvent:MouseEvent);}); fire(b,'click',MouseEvent); }
            else if(how==='touch'){
              try{var r=b.getBoundingClientRect(); var t=new Touch({identifier:1,target:b,clientX:r.left+5,clientY:r.top+5});
                b.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,cancelable:true,touches:[t],targetTouches:[t],changedTouches:[t]}));
                b.dispatchEvent(new TouchEvent('touchend',{bubbles:true,cancelable:true,touches:[],targetTouches:[],changedTouches:[t]}));
              }catch(e){log.push('touch failed: '+e.message);}
            }
            else if(how==='text'){ var tx=b.querySelector('.albumFavoriteText'); if(tx) tx.click(); }
            else if(how==='jquery'){ if(window.jQuery){ window.jQuery(b).trigger('click'); window.jQuery(b).trigger('mousedown'); } else log.push('no jQuery'); }
            return 'pressed';
          };
          if(diag){
            var b=btn();
            log.push('button: '+(b?clip(b.outerHTML,300):'(none)'));
            var jq=window.jQuery;
            if(jq&&jq._data&&b){
              var nodes=[document,window]; var n=b; while(n&&n!==document){nodes.push(n); n=n.parentNode;}
              [].forEach.call(b.querySelectorAll('*'),function(x){nodes.push(x);});
              nodes.forEach(function(x){ var ev=jq._data(x,'events'); if(!ev) return;
                Object.keys(ev).forEach(function(type){ ev[type].forEach(function(h){
                  var src=String(h.handler); var broad=(x===document||x===window||x===document.body||x===document.documentElement);
                  if(broad&&!/fav/i.test(src)&&!/fav/i.test(h.selector||'')) return;
                  log.push('jq handler '+(x.nodeName||'window')+' ['+type+(h.selector?' '+h.selector:'')+']: '+clip(src,1500)); }); }); });
            }
            log.push('inline onclick on button: '+(b&&b.onclick?clip(String(b.onclick),600):'none'));
            // Decode khinsider's packed page scripts and show the favourite code.
            [].forEach.call(document.scripts,function(s){
              var t=(s.textContent||'').trim(); if(!t) return;
              var code=t;
              if(/^eval\(function\(p,a,c,k,e,[rd]\)/.test(t)){ try{ code=new Function('return '+t.slice(4))(); }catch(e){ log.push('unpack failed: '+e.message); return; } }
              ['album_favorite_toggle','albumFavoriteButton','albumFavorite'].forEach(function(key){
                var i=code.indexOf(key); if(i<0) return;
                log.push('code near '+key+': '+clip(code.slice(Math.max(0,i-700),i+900),1600));
              });
            });
            var globals=Object.keys(window).filter(function(k){return /fav/i.test(k);});
            log.push('globals with fav: '+globals.join(', '));
          }
          return window.__vgState();
        })($diagnostic)
        """.trimIndent()
}
