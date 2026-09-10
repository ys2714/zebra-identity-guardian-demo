# Zebra Identity Guardian Demo

Two small Android apps that demonstrate the [Zebra Identity Guardian](https://techdocs.zebra.com/identityguardian/3-1/api/)
**content provider based API**, split the way a shift actually uses it: a crew
member authenticates, a lead reads back who is signed in.

Kotlin + Jetpack Compose, one screen each, one API each.

| App | Folder | Application ID | APIs it calls | Content URI |
| --- | --- | --- | --- | --- |
| **IG Crew** | [`crew-app/`](crew-app) | `com.zebra.igcrew` | Start Authentication, Verification 3 (`authenticationScheme3`); Get Authentication Status | `content://com.zebra.mdna.els.provider/` via `ContentResolver.call("lockscreenaction", "startauthentication")`, then `…/lockscreenaction/authenticationstatus` via `query()` |
| **IG Lead** | [`lead-app/`](lead-app) | `com.zebra.iglead` | Get Current User Session (v2) | `content://com.zebra.mdna.els.provider/v2/currentsession` via `ContentResolver.query()` |

The application IDs differ, so both install side by side on the same device.
Each is a self-contained Gradle project — open either folder in Android Studio.

## crew-app — Start Authentication

Authentication begins on its own as soon as the app has its delegation scopes:
Start Authentication with `user_verification=authenticationScheme3` (labelled
*Verification 3* in the Identity Guardian configuration) and
`launchflag=blocking`, which brings up the Identity Guardian lock screen. There
is a button to re-run it, and an **Exit** button.

Start Authentication answers **the moment Identity Guardian accepts the
request**, so its `RESULT` says only whether the lock screen was launched —
normally `IN_PROGRESS`. It is Get Authentication Status that reports how the user
got on with that screen, which crew-app queries once its own activity is resumed
and the lock screen is therefore out of the way. `SUCCESS` shows **Authentication
succeeded.**; `BUSY`, `ERROR`, an unrecognised status or a failed call are each
reported with what came back.

`SUCCESS` describes the lock screen and the verification that ran on it, not the
credentials the user typed — reading back who ended up signed in is lead-app's
job.

## lead-app — Get Current User Session

A login form. The app calls Get Current User Session on launch and again each
time it returns to the foreground, filling **User** from the session's `userId`
and **Role** from its `userRole`, both drawn in red to show they came from
Identity Guardian. **Password** is the one field the session does not supply, and
**Login** is local to the demo: it checks the form is complete and reports who
would be signed in. No credential leaves the app.

The v2 payload is camelCase and nests both of those values inside
`userInformation`, so the parser flattens the object and matches keys by name at
any depth rather than reading the top level only — see
[`lead-app/README.md`](lead-app/README.md#the-v2-payload-is-camelcase-and-nested).

Being a lead's tool, it also gates on the role: the roles listed in
`lead-app/app/src/main/res/values/arrays.xml` (shipping with `Parttimer`) get a
*not available for this role* screen instead of the form. It is a deny list, so
any other role still gets through.

## What both apps have to do first

Identity Guardian does **not** gate its provider on an Android permission. Its
provider asks Zebra Device Manager whether the caller holds a *delegation scope*
for the API URI being called, and answers `Caller is unauthorized` without one.

Each app grants itself those scopes on launch, in two steps — an MX AccessMgr
*AllowCaller* profile applied through EMDK (this package + signature may call
the service named by the URI), then a ZDM delegation token for the same URI.
A scope is granted per calling package and per API URI, so each app does this
for every API it calls. An administrator can equally stage the same profiles
with StageNow or an EMM; the in-app attempt is then redundant but harmless.

[`lead-app/README.md`](lead-app/README.md) documents this in full, including
three ways the MX step fails *silently* and how to tell it actually worked.

## Device prerequisites

A Zebra device with Identity Guardian installed and configured (Verification 3
set up, for crew-app), plus EMDK and Zebra Device Manager on the device for the
authorization step. Verified on an EM45 (Android 15) with Identity Guardian
3.1.000.1204, EMDK 15.0.82 and MX 15.2.0.10.

EMDK is a device-provided shared library, so the APKs still install on non-Zebra
devices — they just report that EMDK is unavailable instead of allowlisting.

## Building

Requires JDK 17+ (Android Studio's bundled JBR works) and the Android SDK; set
`sdk.dir` in each project's `local.properties`.

```bash
cd lead-app  && ./gradlew assembleDebug testDebugUnitTest
cd ../crew-app && ./gradlew assembleDebug testDebugUnitTest
```

`./gradlew releaseApk` in either folder builds the signed release variant,
renames it to `zebra-ig-lead-demo-<tag>.apk` / `zebra-ig-crew-demo-<tag>.apk`
using `git describe --tags --always`, and copies it to that project's root.
Both sign with the same release key (`zebra-ig-demo-key`), so the pair shares
one `CallerSignature`; credentials come from the git-ignored
`keystore.properties` described in each project's `keystore.properties.template`.

## Trying the pair

1. Install both APKs on the device.
2. Open **IG Crew** — it starts authenticating by itself. Sign in on the
   Identity Guardian lock screen; coming back, the screen reports
   *Authentication succeeded.*
3. Open **IG Lead** — the form comes up carrying that user and role. Signing in
   as a `Parttimer` gets the *not available for this role* screen instead.
