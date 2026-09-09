# Zebra Identity Guardian Demo

Two small Android apps that demonstrate the [Zebra Identity Guardian](https://techdocs.zebra.com/identityguardian/3-1/api/)
**content provider based API**, split the way a shift actually uses it: a crew
member authenticates, a lead reads back who is signed in.

Kotlin + Jetpack Compose, one screen each, one API each.

| App | Folder | Application ID | API it calls | Content URI |
| --- | --- | --- | --- | --- |
| **IG Crew** | [`crew-app/`](crew-app) | `com.zebra.igcrew` | Start Authentication, Verification 3 (`authenticationScheme3`) | `content://com.zebra.mdna.els.provider/` via `ContentResolver.call("lockscreenaction", "startauthentication")` |
| **IG Lead** | [`lead-app/`](lead-app) | `com.zebra.iglead` | Get Current User Session (v2) | `content://com.zebra.mdna.els.provider/v2/currentsession` via `ContentResolver.query()` |

The application IDs differ, so both install side by side on the same device.
Each is a self-contained Gradle project — open either folder in Android Studio.

## crew-app — Start Authentication

One button. Tapping it calls Start Authentication with
`user_verification=authenticationScheme3` (labelled *Verification 3* in the
Identity Guardian configuration) and `launchflag=blocking`, which brings up the
Identity Guardian lock screen. When Identity Guardian answers `RESULT=SUCCESS`
the screen shows **Authentication succeeded.**; `IN_PROGRESS`, `BUSY`, `ERROR`,
an unrecognised status or a failed call are each reported with what came back.

`RESULT` describes the lock screen Identity Guardian put up, not the credentials
the user then types into it — reading back who ended up signed in is lead-app's
job.

## lead-app — Get Current User Session

A login form. On launch the app calls Get Current User Session once and fills
**User** from the session's `user_id` and **Role** from its `user_role`, both
drawn in red to show they came from Identity Guardian. **Password** is the one
field the session does not supply, and **Login** is local to the demo: it checks
the form is complete and reports who would be signed in. No credential leaves
the app.

## What both apps have to do first

Identity Guardian does **not** gate its provider on an Android permission. Its
provider asks Zebra Device Manager whether the caller holds a *delegation scope*
for the API URI being called, and answers `Caller is unauthorized` without one.

Each app grants itself that scope on launch, in two steps — an MX AccessMgr
*AllowCaller* profile applied through EMDK (this package + signature may call
the service named by the URI), then a ZDM delegation token for the same URI.
A scope is granted per calling package, so each app does this for the one API it
calls. An administrator can equally stage the same profiles with StageNow or an
EMM; the in-app attempt is then redundant but harmless.

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
2. Open **IG Crew**, tap *Start Authentication*, and sign in on the Identity
   Guardian lock screen.
3. Open **IG Lead** — the form comes up carrying that user and role.
