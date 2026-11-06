# StockKeep

A simple, offline Android app for keeping track of what you have at home: photo, name, quantity, date bought, expiry date, open life and a link to buy more.

Java · Material 3 · Room (SQLite) · Android 8.0+ · no internet or account needed.

## Get the APK (pick one)

**A. Android Studio (easiest on a computer)**
1. Open this folder in Android Studio (File → Open).
2. Wait for the first sync to finish (it downloads the Android libraries).
3. Phone plugged in with USB debugging on → press ▶ Run.
   Or: Build → Build App Bundle(s) / APK(s) → Build APK(s), then copy the APK to your phone.

**B. GitHub (no installs, works from a phone)**
1. Put this folder in a **private** GitHub repository and push it.
2. Open the repository's **Actions** tab → "Build APK" runs automatically.
3. When it finishes, download **StockKeep-apk** from the run page, unzip it and open the APK on your phone.

**C. Docker**
```
docker build -t stockkeep-builder .
docker run --rm -v "$PWD":/app stockkeep-builder
```
The APK appears in `app/build/outputs/apk/release/`.

On the phone, Android will ask you to allow installing apps from the browser or file manager the first time.

## Updating later without losing data

Android keeps an app's data when a new APK is installed over it, as long as:

1. **The package name stays `com.stockkeep.app`** (in `app/build.gradle`).
2. **It is signed with the same key.** That key is `keystore/stockkeep.jks` (passwords in `keystore.properties`). Every build is signed with it, whichever method you use. **Keep a copy of both files somewhere safe.** If they are lost, a new version can't be installed over the old one.
3. **`versionCode` goes up** for each new version (1 → 2 → 3 …).

If a new version changes the database, it must include a Room *migration* (see the comment in `AppDatabase.java`). The app never wipes data on upgrade.

As an extra safety net, use **Settings → Export backup** now and then. It saves everything (including photos) to one `.zip` file that **Restore backup** can read back, even on a new phone.

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
