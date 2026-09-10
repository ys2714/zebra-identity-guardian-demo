# Zebra Identity Guardian Crew Demo

An Android app that authenticates a crew member through the Identity Guardian
**Start Authentication** API with **Verification 3**, then reports how it went
using **Get Authentication Status**.

It is the crew-facing counterpart to [`../lead-app`](../lead-app), which demos
Get Current User Session. The two apps have different application IDs
(`com.zebra.igcrew` vs `com.zebra.iglead`), so they can be installed side by side.

## What it does

1. On launch, it authorizes itself for the two API URIs it calls
   (`…/lockscreenaction/startauthentication` and
   `…/lockscreenaction/authenticationstatus`, both under
   `content://com.zebra.mdna.els.provider/`) by applying an MX AccessMgr
   *AllowCaller* profile via EMDK and acquiring the matching ZDM delegation
   tokens. Identity Guardian answers "Caller is unauthorized" without those
   scopes.
2. As soon as those scopes are in place it calls Start Authentication by itself,
   with `user_verification=authenticationScheme3` (labelled *Verification 3* in
   the Identity Guardian configuration) and `launchflag=blocking`. No tap is
   needed on launch; the button re-runs it afterwards.
3. While the lock screen is up the screen says so rather than claiming a result.
4. When the app comes back to the foreground it calls Get Authentication Status
   and reports that: `SUCCESS` shows *Authentication succeeded.*, and `BUSY`,
   `ERROR`, an unrecognised status or a failed call are each reported with what
   came back.
5. **Exit** closes the app and drops it from Recents.

## Why two APIs

Start Authentication answers **the moment Identity Guardian accepts the
request**, so its `RESULT` says whether the lock screen was *launched* — it is
normally `IN_PROGRESS` — and never says what the user then did on that screen.
Treating it as the outcome leaves the screen stuck on `IN_PROGRESS` after a
perfectly good sign-in, which is what an earlier version of this demo did.

Get Authentication Status is the API that reports where the lock screen got to.
This app queries it when its own activity is resumed, because with
`launchflag=blocking` the lock screen sits in front of this app until the user
is finished with it — so being resumed is the signal that the status has
settled. A status still reading `IN_PROGRESS` means the user came back without
finishing, and the wait simply continues.

`SUCCESS` still describes the lock screen and the configured verification, not
the credentials themselves. Reading back *who* ended up signed in is Get Current
User Session, which is what `lead-app` demonstrates.

## Requirements

- A Zebra device with Identity Guardian (`com.zebra.mdna.els`) installed and
  Verification 3 configured, plus EMDK and Zebra Device Manager on the device.
- Android SDK 36; JDK 17.

## Build

```sh
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # local unit tests
./gradlew releaseApk             # signed APK, renamed and copied to this folder
```

`releaseApk` needs `keystore.properties` (see `keystore.properties.template`);
it is git-ignored and points at the release keystore.
