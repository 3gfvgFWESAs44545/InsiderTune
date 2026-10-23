/*
 * Pixel Notes — makes every khinsider HTTP client follow Settings › Internet.
 *
 * The proxy and DNS-over-HTTPS settings are stored on [YouTube] (that is where ArchiveTune kept
 * them). These hooks read them on every connection, so changing a setting takes effect without
 * rebuilding clients that were created earlier.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.innertube.khinsider

import moe.rukamori.archivetune.innertube.YouTube
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

object AppNetwork {
    private val dns =
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = YouTube.dns.lookup(hostname)
        }

    private fun selector(forMedia: Boolean) =
        object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val proxy = if (forMedia) YouTube.streamProxy else YouTube.proxy
                return listOf(proxy ?: Proxy.NO_PROXY)
            }

            override fun connectFailed(
                uri: URI?,
                sa: SocketAddress?,
                ioe: IOException?,
            ) = Unit
        }

    private val proxyAuthenticator =
        Authenticator { _, response ->
            val user = YouTube.proxyUsername
            val password = YouTube.proxyPassword
            when {
                user.isNullOrBlank() || password.isNullOrBlank() -> null
                // Already tried these credentials; give up rather than loop.
                response.request.header("Proxy-Authorization") != null -> null
                else ->
                    response.request
                        .newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(user, password))
                        .build()
            }
        }

    /**
     * Applies the user's proxy and DNS-over-HTTPS settings.
     * [forMedia] honours "Bypass proxy for streaming" (used for audio file downloads).
     */
    fun OkHttpClient.Builder.followAppNetworkSettings(forMedia: Boolean = false): OkHttpClient.Builder =
        dns(dns)
            .proxySelector(selector(forMedia))
            .proxyAuthenticator(proxyAuthenticator)
}
