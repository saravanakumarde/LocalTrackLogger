# Updating without losing data

Android keeps an app's data on update only if the new APK has the same package name, a
`versionCode` that is not lower, and the **same signing key**.

* All builds (local and CI) are signed with `app/signing/personal.jks`, so every APK built from
  this repo installs over the previous one.
* CI sets `versionCode` from the GitHub run number, so each build is newer than the last.
* Install the APK from the CI artifact (or run from Android Studio) and tap *Update*. Do not uninstall.

The keystore password is in `app/build.gradle.kts`. That is fine for a personal sideloaded app. If you
ever publish the app, create a private key, keep it out of git and move the passwords to CI secrets.

Back up at any time from Settings > Export all tracks. Settings > Import GPX / ZIP restores them.
