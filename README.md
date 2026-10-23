<div align="center">

<img src="assets/pixelnotes-icon.png" width="160" height="160" alt="Pixel Notes icon">

# Pixel Notes

**A video game music player for Android, powered by [khinsider](https://downloads.khinsider.com).**

Browse, search and stream more than 100,000 video game soundtracks in a polished, modern music app.

[Download the latest APK](https://github.com/3gfvgFWESAs44545/Pixel-Notes/releases/latest) · [Features](#features) · [How it works](#how-it-works) · [Credits](#credits)

</div>

---

## What is Pixel Notes?

Pixel Notes is an Android music app for video game soundtracks. All of the music comes from
**khinsider** (downloads.khinsider.com), one of the largest free archives of game music on the web.

The app is a modified version of **[ArchiveTune](https://github.com/rukamori/ArchiveTune)**, an
excellent open-source YouTube Music client. Pixel Notes keeps ArchiveTune's player, library and
Material 3 design, and replaces YouTube Music with khinsider as the music source. There's no
YouTube, no Google account and no ads.

It was built for and tested on a Pixel 9 Pro, and runs on any phone with Android 8.0 or newer.

## Features

### Discover
- **Home**: latest additions to khinsider, your liked songs and albums, a **Jump back in**
  carousel of albums you've recently played, and rows from the Top 40, Most Favorites,
  Top 100 (last 6 months), Top 1000 All Time and Currently Viewed charts. Chart rows show a
  random selection on each visit.
- **Browse**: every khinsider chart plus album types (gamerips, soundtracks, arrangements,
  remixes…), 35 platforms (Switch, PS1–PS5, SNES, N64, Game Boy, Xbox, Steam and more) and
  every year back to 1980. Each list scrolls through khinsider's full catalogue.
- **Search** across the whole archive, most popular albums first.

### Listen
- Full-featured player with queue, shuffle, repeat, sleep timer, equaliser and lock-screen controls.
- Streams MP3s, or **FLAC** when an album has it and audio quality is set to *Highest*.
- Download songs and albums for offline listening.
- **Jump back in** opens an album at the last song you played and resumes it.

### Library
- Like albums and songs, make playlists, see your most-played albums and your top 50.
- Shortcuts for Liked songs, Liked albums, Recently played, Offline, My top 50, Playlists and Artists.

### Optional khinsider account
Signing in is entirely optional; the app works fully without an account. If you sign in
(Settings → khinsider account):
- **Favourites sync both ways**: liking an album in the app adds it to *My Favorites* on
  khinsider and unliking removes it. *Sync now* brings your khinsider favourites into the app.
- **History**: albums you play are added to your khinsider history.
- **Playlists**: your khinsider playlists are imported into the app.

You sign in on khinsider's own login page inside the app. Pixel Notes never sees or stores your
password, only the session that keeps you signed in, and *Sign out* clears it.

### Light on khinsider
Pages are cached on your phone (chart lists for 6 hours, the home page for 3 hours, album pages
for 3 days), so the app doesn't keep re-fetching the site. If khinsider can't be reached, the
last saved copy is used.

## Install

1. On your Android phone, open the
   [latest release](https://github.com/3gfvgFWESAs44545/Pixel-Notes/releases/latest).
2. Download the `.apk` file and open it.
3. If Android asks, allow installs from your browser, then tap **Install**.

Each push to `main` builds a new signed APK automatically and publishes it as a release. The
current build is for 64-bit ARM phones (arm64), which covers almost every modern Android phone.

## How it works

khinsider has no public API, so Pixel Notes reads its web pages the way a browser would:

| What | Where it comes from |
| --- | --- |
| Charts, categories, platforms, years | khinsider list pages (`/top40`, `/most-favorites`, `/game-soundtracks/...`) |
| Search | `/search?search=...&sort=popularity` |
| Albums and track lists | Album pages (`/game-soundtracks/album/<slug>`) |
| Audio | The MP3/FLAC file linked from each track's page, streamed directly |
| Favourites sync | khinsider's own `/cp/album_favorite_toggle` request, sent with your signed-in session |

Under the hood, a small khinsider module (`core/.../innertube/khinsider/`) parses these pages
with OkHttp and jsoup and turns them into the album, song and page models ArchiveTune already
uses. The rest of the app (player, library, database, UI) is ArchiveTune's, with
YouTube-specific features removed or hidden.

## Building it yourself

Builds run on GitHub Actions (`.github/workflows/insidertune.yml`). To build locally you need
JDK 21 and the Android SDK:

```bash
git clone https://github.com/3gfvgFWESAs44545/Pixel-Notes.git
cd Pixel-Notes
./gradlew assembleFossMobileArm64Release
```

The APK is written to `app/build/outputs/apk/`. Release builds are signed with the personal key
in `app/keystore/` (passwords are set in the workflow). If you fork this project for wider
distribution, generate your own key and keep it out of the repository.

## Support khinsider

Every soundtrack in this app is hosted by khinsider, which has been preserving game music for free
for many years. Running a site this size costs real money. **If you enjoy the music,
please consider donating to khinsider.** Their [FAQ](https://downloads.khinsider.com/faq)
explains how.

## Credits

- **[ArchiveTune](https://github.com/rukamori/ArchiveTune)** by Rukamori and the
  [ArchiveTune contributors](https://github.com/rukamori/ArchiveTune/graphs/contributors): the app
  this is built on. Thank you for such a great foundation. ArchiveTune itself builds on InnerTune,
  Metrolist and other open-source projects; see the in-app About page and licences.
- **[khinsider](https://downloads.khinsider.com)**: the music archive, and the community behind it.
- **[khinsiderdl-web](https://github.com/kobayashi90/khinsiderdl-web)** by kobayashi90: its
  khinsider page parsing was the reference for Pixel Notes's khinsider support.

## Licence and disclaimer

Pixel Notes is free software under the **GNU General Public License v3.0**, the same licence as
ArchiveTune. See [LICENSE](LICENSE).

Pixel Notes is an unofficial fan project. It is not affiliated with or endorsed by khinsider,
ArchiveTune, or any game publisher. All music belongs to its respective owners.
