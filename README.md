# StockKeep

A simple, offline Android app for keeping track of what you have at home: photo, name, quantity, date bought, expiry date, open life and a link to buy more.

Java · Material 3 · Room (SQLite) · Android 8.0+ · no internet or account needed.

## Install it

1. On your Android phone, open **https://github.com/ahmad-al-asad/inventory/releases/latest**
2. Tap **StockKeep-x.y.z.apk** to download it, then open it.
3. If Android asks, allow your browser to "install unknown apps", then tap **Install**.

Optional **app lock** (Settings → Security) uses your phone's fingerprint, face or PIN.

Everything you add stays on your own phone. The app has no internet permission and no account, so nothing is shared with anyone, including the developer.

## Build it yourself

- **Android Studio:** open this folder and press Run.
- **Docker:** `docker build -t stockkeep-builder . && docker run --rm -v "$PWD":/app stockkeep-builder`

Without the maintainer's signing key, your build is signed with a debug key. It works, but it can't be installed over the official release (uninstall that first).

## For the maintainer: signing and updates

The signing key is **not** in this repository.
- Locally: keep `release.jks` and `signing.properties` in the project folder (gitignored).
- GitHub Actions: repository secrets `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD`.

To publish an update without users losing data: keep `applicationId` the same, sign with the same key, and increase `versionCode` (and `versionName`) in `app/build.gradle`. Pushing to `main` then publishes a new release automatically. Database changes need a Room migration (see `AppDatabase.java`).

Users can also back up everything in **Settings → Export backup**.

## How stock is organised

- **Item**: the product (name, photo, unit, buy link, open life, notification switches).
- **Batch**: units of that item sharing the same dates. Sealed units bought together are one batch; each opened unit is its own batch with its own opened date.
- **Use by** for a batch = the earlier of the printed expiry and *opened date + open life*.
- **Use one** takes from opened units first, then from the batch that expires soonest.
- **Notifications**: when an item's total drops to 1, when it reaches 0, and a daily morning summary of expired / soon-to-expire items.

## Project layout

```
app/src/main/java/com/stockkeep/app/
  data/     Item, Batch, database, repository, photo storage, backup
  logic/    expiry and date calculations
  notify/   stock alerts and the daily expiry check
  ui/       screens (list, detail, add/edit, settings)
app/src/main/res/   layouts, strings, colours, icons
```
