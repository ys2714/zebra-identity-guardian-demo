# zebra-identity-guardian-demo

A simple demo app that shows the basic capabilities of [Zebra Identity Guardian](https://techdocs.zebra.com/identityguardian/3-1/api/)
through its **content provider based API**.

Kotlin + Jetpack Compose, one screen, two buttons:
<img width="270" height="540" alt="Screenshot_20260904_222015" src="https://github.com/user-attachments/assets/04ca403a-6994-41cf-8573-8b85bf641fc3" />

| Button | API | Content URI |
| --- | --- | --- |
| **Start Authentication** | Start Authentication | `content://com.zebra.mdna.els.provider/` (via `ContentResolver.call`) |
| **Get Current User Session** | Get Current User Session (v2) | `content://com.zebra.mdna.els.provider/v2/currentsession` |

Whatever Identity Guardian returns — the `RESULT` status, the parsed session
fields, the raw JSON, or the error and a hint about how to fix it — is rendered
in the result card below the buttons.

## How the API is called

Both APIs go through the `com.zebra.mdna.els.provider` content provider, but in
two different ways:

- **Start Authentication** uses `ContentResolver.call()`, with `lockscreenaction`
  as the *method* and `startauthentication` as the *arg*. The input Bundle carries
  `user_verification` (the authentication scheme) and `launchflag` (`blocking` or
  `unblocking`). The response Bundle's `RESULT` is one of `SUCCESS`,
  `IN_PROGRESS`, `BUSY` or `ERROR`.
- **Get Current User Session** uses `ContentResolver.query()`. The payload does
  *not* come back as cursor rows — it is a stringified JSON object in the
  cursor's `extras` under the `RESULT` key.

See `app/src/main/java/com/zebra/igdemo/ig/` for the client and the constants.

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

The demo grants itself that scope on launch, in the two steps Zebra's delegation
model needs, and shows the outcome in the status line above the buttons:

1. **MX AccessMgr profile** (`com.zebra.igdemo.mx.AccessManager`, applied through
   EMDK) — allows this package + signature to call the service named by the URI:

   | Parameter | Value |
   | --- | --- |
   | `OperationMode` | `1` (Single User without Whitelist) |
   | `ServiceAccessAction` | `4` (AllowCaller) |
   | `ServiceIdentifier` | one API URI, e.g. `content://com.zebra.mdna.els.provider/lockscreenaction/startauthentication` |
   | `CallerPackageName` | this app's package |
   | `CallerSignature` | Base64 of this app's signing certificate, read at runtime |

   `CallerSignature` is the same certificate StageNow asks you to export with
   `SigTools.jar getcert`; the app reads it from its own `PackageInfo` instead.

2. **ZDM delegation token** (`com.zebra.igdemo.zdm.ZdmDelegation`) — queries
   `content://com.zebra.devicemanager.zdmcontentprovider/AcquireToken` with
   `delegation_scope=<the same URI>`. Step 1 only *permits* asking for the token;
   this call is what records the delegation Identity Guardian then finds. It needs
   `com.zebra.devicemanager.provider.READ_PERMISSION` (protection level `normal`).

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
  single service literally named `a,b,c`. The demo applies one profile per URI.
- **Use a current CSP version.** The profile declares
  `<characteristic type="AccessMgr" version="15.0">`, matching
  `assets/dsd/AccessMgr.dsd` in `com.symbol.mxmf` on the device.

The profile XML lives in `app/src/main/assets/profile_access_manager_allow_call_service.xml`
and the EMDK plumbing in `app/src/main/java/com/zebra/igdemo/mx/`, modelled on
[zebra-sdk-kotlin-wrapper](https://github.com/ys2714/zebra-sdk-kotlin-wrapper)'s
`callAccessManagerAllowCallService()`.

Note that `OperationMode = 1` turns app install/launch whitelisting *off* on the
device, which is what the reference profile does. Set it to `0` ("do not
change") in the asset if the device's whitelisting policy must stay untouched.

An administrator can of course still stage the same AccessMgr profiles with
StageNow or an EMM; the app's own attempt is then redundant but harmless.

## Device prerequisites

The demo only works on a Zebra device with Identity Guardian installed and
configured, plus EMDK and Zebra Device Manager on the device for the
authorization step. It needs:

1. The permissions declared in the manifest:
   `com.symbol.emdk.permission.EMDK` (apply the MX profile),
   `com.zebra.devicemanager.provider.READ_PERMISSION` (acquire the ZDM token)
   and `com.zebra.mdna.els.permission.PROVIDER` (asked for by the IG docs).
2. The delegation scopes described above. The app grants them itself on launch;
   an administrator can equally stage the AccessMgr profiles with StageNow or an
   EMM.

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
`zebra-ig-demo-<tag>.apk` using `git describe --tags --always`, and copies it to
the project root:

```bash
./gradlew releaseApk
```

Signing credentials are read from `keystore.properties`, which is git-ignored.
Copy `keystore.properties.template` to `keystore.properties` and create the
keystore with the `keytool` command documented in that template — the key alias
is `zebra-ig-demo-key`. Debug builds work without it.

## Tests

```bash
./gradlew testDebugUnitTest
```

Covers the parsing of the session JSON payload (`UserSessionTest`) and the MX
profile placeholder substitution (`MxProfileTemplateTest`).
