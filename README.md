# Thumbline

A minimalist Android launcher featuring bottom-aligned home apps, quick icon shortcuts, and clean on-device customization.

Download the latest APK: [thumbline.apk](https://github.com/akpandeya/Olauncher/releases/latest/download/thumbline.apk) (install/update via Obtainium).

Based on [Olauncher](https://github.com/tanujnotes/Olauncher) by Tanuj (GPLv3).

## Features

The Settings screen has these sections:

- Home screen: bottom-aligned home apps.
- Shortcut icons: quick icon shortcuts.
- Drawer: the app drawer.
- Wellbeing: nudges and wellbeing notices.
- Today: a page with your next event, weather and more.
- Deutsch: German word help. Choose a Dictionary app for “Open in…” in drawer lookups,
  shared-word lookups and the saved Words list (long-press a word). Text is handed to
  the chosen app via Process Text, then Share as a fallback. “Other app (copy word)”
  also lets you choose apps without either handler: the word is copied and the app
  opens so you can paste it. Wiktionary remains the default; automatic meanings
  still use the separate “Look up meanings online” setting.
- Appearance and Gestures: look and swipe actions.
- Backup: export and import your settings.
- Privacy: shows the permissions below and whether each is granted.

Thumbline does not use analytics or ads.

## Permissions

Most permissions are optional and are asked for only when you switch the feature on. If you say no, the app keeps working.

| Permission | What it is for | How to turn it off |
| --- | --- | --- |
| VIBRATE | Vibrate for touch feedback | Always on (cannot be revoked) |
| SET_WALLPAPER | Set your wallpaper when you choose a photo | Always on (cannot be revoked) |
| EXPAND_STATUS_BAR | Open the notification shade with a swipe | Always on (cannot be revoked) |
| QUERY_ALL_PACKAGES | List the apps you have installed | Always on (cannot be revoked) |
| REQUEST_DELETE_PACKAGES | Ask the system to uninstall an app when you choose to | Always on (cannot be revoked) |
| SET_ALARM | Open the alarm clock from the home screen | Always on (cannot be revoked) |
| ACCESS_HIDDEN_PROFILES | Show apps in your private space | Always on (cannot be revoked) |
| INTERNET | Allow the optional network features below | Always on (cannot be revoked) |
| PACKAGE_USAGE_STATS | Read your screen time, only on this phone | Android Settings, Usage access |
| POST_NOTIFICATIONS | Show gentle nudges and wellbeing notices | Android App info |
| ACCESS_COARSE_LOCATION | Find your approximate location for the weather | Android App info |
| READ_CALENDAR | Show your next event on Today | Android App info |
| READ_CONTACTS | Search your contacts from the drawer | Android App info |
| Health Connect: steps, sleep, heart rate, resting heart rate, exercise | Show steps, sleep and heart rate | Health Connect settings |
| WRITE_SECURE_SETTINGS | Turn the whole screen grey (set only through a computer) | Revoke it with adb |

## Where data goes

Your settings and data stay on your phone. Thumbline has no server and no analytics. Four optional features use the internet:

| Feature | What is sent | To whom | How to turn it off |
| --- | --- | --- | --- |
| Weather | Your rounded location | Open-Meteo | Turn off in Settings |
| Word meanings | The word you look up | Wiktionary | Turn off in Settings |
| Currency rates | A request for exchange rates | Frankfurter | Turn off in Settings |
| Now playing | A request for album artwork | The artwork address given by your music app | Turn off in Settings |

Health Connect data is read live and is not stored by Thumbline.

## Update and backup

Updating: new builds are published as GitHub releases. Every build is the debug variant, signed with the same key, so a new APK installs over the old one and keeps your data. Obtainium can do this for you.

Backup (Settings, Backup):

- Export backup saves a file with your settings, saved items, words, word progress, screen-time days and intention history.
- Import backup replaces all Thumbline settings with the file. Device admin and accessibility grants are not restored.
- Nightly backup saves a backup every night to a folder you pick. It keeps the last 7.
- Not included: photo wallpapers, weather and rate caches, and held notifications.

## License

[GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html)
