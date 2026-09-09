# Zebra Identity Guardian Crew Demo

A one-button Android app that calls the Identity Guardian **Start Authentication**
API with **Verification 3** and reports whether it succeeded.

It is the crew-facing counterpart to [`../lead-app`](../lead-app), which demos
Get Current User Session. The two apps have different application IDs
(`com.zebra.igcrew` vs `com.zebra.iglead`), so they can be installed side by side.

## What it does

1. On launch, it authorizes itself for the one API URI it calls
   (`content://com.zebra.mdna.els.provider/lockscreenaction/startauthentication`)
   by applying an MX AccessMgr *AllowCaller* profile via EMDK and acquiring the
   matching ZDM delegation token. Identity Guardian answers "Caller is
   unauthorized" without that scope.
2. Tapping **Start Authentication** calls the provider with
   `user_verification=authenticationScheme3` (labelled *Verification 3* in the
   Identity Guardian configuration) and `launchflag=blocking`.
3. When the `RESULT` is `SUCCESS`, the screen shows *Authentication succeeded.*
   `IN_PROGRESS`, `BUSY`, `ERROR`, an unrecognised status, or a failed call are
   each reported with what came back.

`RESULT` describes the lock screen Identity Guardian put up, not the credentials
the user typed into it — `SUCCESS` means the screen was locked and the configured
verification ran. Reading back who ended up signed in is Get Current User Session,
which is what `lead-app` demonstrates.

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
