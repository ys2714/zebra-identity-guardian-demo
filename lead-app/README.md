# Zebra Identity Guardian Lead Demo

The lead-facing half of a pair of demo apps for [Zebra Identity Guardian](https://techdocs.zebra.com/identityguardian/3-1/api/)
and its **content provider based API**. Its sibling, [`../crew-app`](../crew-app),
covers Start Authentication; this one covers **Get Current User Session (v2)**.

| App | Application ID | API | Content URI |
| --- | --- | --- | --- |
| **lead-app** (this one) | `com.zebra.iglead` | Get Current User Session (v2) | `content://com.zebra.mdna.els.provider/v2/currentsession` |
| [crew-app](../crew-app) | `com.zebra.igcrew` | Start Authentication (Verification 3) | `content://com.zebra.mdna.els.provider/` (via `ContentResolver.call`) |

The application IDs differ, so both install side by side on the same device.

Kotlin + Jetpack Compose, one screen: a login form laid out as in
[`../lead-app-UI-layout.png`](../lead-app-UI-layout.png).

- On launch the app authorizes itself (see below) and calls Get Current User
  Session once. The session's `user_id` fills the **User** field and its
  `user_role` fills the **Role** line; both are drawn in red to show they came
  from Identity Guardian.
- **Password** is the one field the session does not supply.
- **Login** is local to the demo: it checks the form is complete and reports who
  would be signed in. No credential leaves the app.
- Anything that goes wrong — authorization, the session read, an incomplete form
  — is reported under the button, with the provider's hint and a retry where one
  applies.

## How the API is called

Get Current User Session uses `ContentResolver.query()`. The payload does *not*
come back as cursor rows — it is a stringified JSON object in the cursor's
`extras` under the `RESULT` key, which `UserSession` parses into ordered fields.

See `app/src/main/java/com/zebra/iglead/ig/` for the client and the constants.

## Authorizing itself as a caller

Identity Guardian does **not** gate its provider on an Android permission. Its
provider asks Zebra Device Manager (ZDM) whether the caller holds a *delegation
scope* for the API URI being called; without one it answers `Caller is
unauthorized` and logs:

```
SecurityHelper: zdm call
RESULT: : Delegation scoped is not granted
ELS-ZBAContentProvider: Caller is unauthorized
```

The app grants itself that scope on launch, in the two steps Zebra's delegation
model needs, and reports the outcome under the login form:

1. **MX AccessMgr profile** (`com.zebra.iglead.mx.AccessManager`, applied through
   EMDK) — allows this package + signature to call the service named by the URI:

   | Parameter | Value |
   | --- | --- |
   | `OperationMode` | `1` (Single User without Whitelist) |
   | `ServiceAccessAction` | `4` (AllowCaller) |
   | `ServiceIdentifier` | one API URI, e.g. `content://com.zebra.mdna.els.provider/v2/currentsession` |
   | `CallerPackageName` | this app's package |
   | `CallerSignature` | Base64 of this app's signing certificate, read at runtime |

   `CallerSignature` is the same certificate StageNow asks you to export with
   `SigTools.jar getcert`; the app reads it from its own `PackageInfo` instead.

2. **ZDM delegation token** (`com.zebra.iglead.zdm.ZdmDelegation`) — queries
   `content://com.zebra.devicemanager.zdmcontentprovider/AcquireToken` with
   `delegation_scope=<the same URI>`. Step 1 only *permits* asking for the token;
   this call is what records the delegation Identity Guardian then finds. It needs
   `com.zebra.devicemanager.provider.READ_PERMISSION` (protection level `normal`).

Both apps do this for themselves: a delegation scope is granted per calling
package, so installing the pair means each grants the scope for the API it calls.

Three things about step 1 are easy to get wrong, and each one fails *silently*:

- **The profile must be declared in `app/src/main/assets/EMDKConfig.xml`.** For a
  profile name it has never seen, EMDK creates an empty one, logs
  `createProfile - mxPresent = false` and returns `SUCCESS` **without ever calling
  MX**. With the profile declared, MX answers `CHECK_XML` and echoes back the
  AccessMgr characteristic it applied. While it was silently skipped, MX also
  "succeeded" for `ServiceAccessAction=99` and for a made-up CSP name — so a
  `SUCCESS` from EMDK alone proves nothing.
- **`ServiceIdentifier` is stored verbatim, not split on commas.** Despite the CSP
  docs describing it as a comma-separated list, passing `a,b,c` allowlists a
  single service literally named `a,b,c`. The app applies one profile per URI.
- **Use a current CSP version.** The profile declares
  `<characteristic type="AccessMgr" version="15.0">`, matching
  `assets/dsd/AccessMgr.dsd` in `com.symbol.mxmf` on the device.

The profile XML lives in `app/src/main/assets/profile_access_manager_allow_call_service.xml`
and the EMDK plumbing in `app/src/main/java/com/zebra/iglead/mx/`, modelled on
[zebra-sdk-kotlin-wrapper](https://github.com/ys2714/zebra-sdk-kotlin-wrapper)'s
`callAccessManagerAllowCallService()`.

Note that `OperationMode = 1` turns app install/launch whitelisting *off* on the
device, which is what the reference profile does. Set it to `0` ("do not
change") in the asset if the device's whitelisting policy must stay untouched.

An administrator can of course still stage the same AccessMgr profiles with
StageNow or an EMM; the app's own attempt is then redundant but harmless.

## Device prerequisites

The app only works on a Zebra device with Identity Guardian installed and
configured, plus EMDK and Zebra Device Manager on the device for the
authorization step. It needs:

1. The permissions declared in the manifest:
   `com.symbol.emdk.permission.EMDK` (apply the MX profile),
   `com.zebra.devicemanager.provider.READ_PERMISSION` (acquire the ZDM token)
   and `com.zebra.mdna.els.permission.PROVIDER` (asked for by the IG docs).
2. The delegation scopes described above. The app grants them itself on launch;
   an administrator can equally stage the AccessMgr profiles with StageNow or an
   EMM.

A session only appears in the form once someone has actually signed in to
Identity Guardian — that is what crew-app's Start Authentication brings up.

Verified on an EM45 (Android 15) with Identity Guardian 3.1.000.1204, EMDK
15.0.82 and MX 15.2.0.10.

EMDK is a device-provided shared library: `app/libs/emdk-9.1.1.jar` is on the
compile classpath only (`compileOnly`) and the manifest declares
`<uses-library android:name="com.symbol.emdk" android:required="false" />`, so
the APK still installs on non-Zebra devices — it just reports that EMDK is
unavailable instead of allowlisting.

## Building

```bash
./gradlew assembleDebug
```

Requires JDK 17+ (Android Studio's bundled JBR works) and the Android SDK; set
`sdk.dir` in `local.properties`.

## Releasing

`releaseApk` builds the release variant, signs it, renames it to
`zebra-ig-lead-demo-<tag>.apk` using `git describe --tags --always`, and copies it
to the project root:

```bash
./gradlew releaseApk
```

Signing credentials are read from `keystore.properties`, which is git-ignored.
Copy `keystore.properties.template` to `keystore.properties` and create the
keystore with the `keytool` command documented in that template — the key alias
is `zebra-ig-demo-key`. crew-app signs with the same key, so the pair shares one
`CallerSignature`. Debug builds work without it.

## Tests

```bash
./gradlew testDebugUnitTest
```

Covers the parsing of the session JSON payload (`UserSessionTest`) and the MX
profile placeholder substitution (`MxProfileTemplateTest`).
