# BanHammer

An Android app that bans you from opening Twitter / X (or any other app or website you pick).
When you open something banned, it instantly sends you back to the home screen and shows a
"that's banned" screen.

## Install

1. Get `app-release.apk` (build it below, or download it from the **Build BanHammer APK**
   GitHub Actions run) and open it on your phone. Allow "install unknown apps" if asked.
2. Open **BanHammer**. The **Home** tab has a setup checklist. Each step has a button that opens
   the exact Android page for it, and the checks update the moment you come back:
   - **Turn on the blocker**: Accessibility → BanHammer → On.
   - **Allow restricted settings** (Android 13+, only if step 1 is greyed out): App info → ⋮ → Allow restricted settings.
   - **Run in the background**: lets the phone's battery saver leave the blocker alone.
3. Twitter/X (`com.twitter.android`) and `x.com` / `twitter.com` are banned by default.
   Use the **Apps** tab to tick any other app (or ban one by package name), and the **Sites** tab
   to add websites. The **Home** tab shows whether the blocker is actually running.
   After updating the app, switch BanHammer off and on again in Accessibility settings.

## The lock

Self-control is hard, so you can **lock** the ban for 1, 7 or 30 days. While locked you can't
switch the ban off or remove anything from it. You can still add more.

## What it can and can't do

- It only blocks. It can't stop you disabling the accessibility service or uninstalling it, so
  the lock stops you cheating in the app, not by force. Add friction yourself: if you want
  more, set up a Digital Wellbeing / parental PIN on Settings.
- The lock uses the phone's clock, so changing the date could shorten it.
- The blocker reacts to accessibility events and also checks the foreground app once a second.
- Website blocking reads the address bar of Chrome, Brave, Edge, Firefox, Samsung Internet,
  Opera and DuckDuckGo. Other browsers, in-app browsers and the X website in a PWA aren't covered.
  Browser updates that rename the address-bar view can break this for that browser.
- No internet permission, no analytics. Settings stay on the device.

## Build

Needs JDK 17+ and the Android SDK (platform 34, build-tools 34).

```
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```
