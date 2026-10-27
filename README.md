# homehound

A minimal TV launcher for Fire TV and Android TV: a clock, a Settings button and a grid of the apps
you installed. No rows, ads, previews or recommendations.

- Hold Select on an app to move it, hide it, open its info or uninstall it.
- Hidden apps sit behind the "Hidden (n)" tile at the end.

## Making Home open homehound

Fire OS and Google TV won't let another app be the default home screen, and after Home they hold
every other app back for 5 seconds. homehound's accessibility service covers the screen with
homehound's last frame the moment Home is pressed and swaps in the real homehound when the system
lets it open, so the stock launcher never shows. The remote does nothing for those first few
seconds.

- **Over adb** (Fire TV or Android TV with network debugging on):

  ```bash
  scripts/setup-device.sh 192.168.100.14
  ```

- **On the TV**: open homehound and choose "Make Home open homehound", then turn on
  "homehound Home button" in Accessibility. On stock Android TV you can instead pick homehound as
  the default home app when asked.

## Fire TV debloat

`scripts/debloat-firetv.sh <ip>` disables the Amazon apps in `scripts/debloat-list.txt` (ads,
tracking, Alexa, shopping, photos, games, Live TV, tutorials, forced updates). Nothing is
uninstalled; `scripts/restore-firetv.sh <ip>` turns them all back on.

## Build

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleRelease
```

The release APK is signed with the shared apphound key named in `keystore.properties` (git-ignored).
versionCode is the commit count.
