# zebra-identity-guardian-demo

A simple demo app that shows the basic capabilities of [Zebra Identity Guardian](https://techdocs.zebra.com/identityguardian/3-1/api/)
through its **content provider based API**.

Kotlin + Jetpack Compose, one screen, two buttons:
<img width="1080" height="2160" alt="Screenshot_20260904_222015" src="https://github.com/user-attachments/assets/04ca403a-6994-41cf-8573-8b85bf641fc3" />

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

## Device prerequisites

The demo only works on a Zebra device with Identity Guardian installed and
configured. Two things are required, and neither is enough on its own:

1. The `com.zebra.mdna.els.permission.PROVIDER` permission — already declared in
   the manifest.
2. **Allowlisting the app per API URI**, via StageNow or your EMM. Identity
   Guardian rejects calls from apps that are not allowlisted, which surfaces in
   this demo as a `SecurityException`. The URIs to allowlist here are
   `lockscreenaction/startauthentication` and `currentsession`.

Without step 2 the app installs and runs fine, but both buttons report
"Access to the Identity Guardian provider was denied."

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

Covers the parsing of the session JSON payload (`UserSessionTest`).
