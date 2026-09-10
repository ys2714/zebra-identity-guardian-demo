# Zebra Identity Guardian Crew Demo

## Overview

- identity guardian API URL: https://techdocs.zebra.com/identityguardian/3-1/api/
- use kotlin language
- use jetpack compose
- add comments to the source code
- simple feature for demostrate identity guardian content provider based API.
- sibling of `../lead-app`, which demos Get Current User Session; this app installs
  alongside it (applicationId `com.zebra.igcrew`)

## UI

- two buttons: Start Authentication, and Exit (closes the app, dropping the task)
- authentication starts automatically, so opening the app is enough; the button
  is for re-running it
- it starts on the **first time the activity's window holds focus**, not from the
  view model's `init` and not from `onResume`. Both of those fire while the
  launch transition is still running, and a lock screen launched over a task that
  has not finished coming to the front is dismissed to the *launcher* rather than
  back to this app. Window focus is the only signal that says this app is really
  what the lock screen came up over
- the call uses Verification 3, i.e. `user_verification=authenticationScheme3`, with
  `launchflag=blocking`
- while the lock screen is up the screen says it is waiting for the user on it,
  and reports no outcome

## Localization

- English in `values/`, Japanese in `values-ja/`; the app follows the device locale
- keep the Identity Guardian API names (Start Authentication, Get Authentication
  Status, Verification 3) and the `RESULT` values (IN_PROGRESS / BUSY / ERROR) in
  English even in the Japanese strings - they are what an operator matches against
  the Identity Guardian configuration and the Zebra docs
- every `%1$s` must survive translation, or `stringResource` throws at runtime
- diagnostic text coming out of EMDK / MX / ZDM / the provider is not localized;
  only the wrapper around it is

## Start Authentication vs Get Authentication Status

- Start Authentication returns as soon as Identity Guardian accepts the request,
  so its `RESULT` only describes the *launch* of the lock screen (normally
  `IN_PROGRESS`) and must never be shown as the authentication outcome
- `BUSY` / `ERROR` from the launch mean the lock screen never came up, so those
  are terminal; anything else means the user is now on the lock screen
- Get Authentication Status is what reports the outcome. `SUCCESS` is the success
  message; `IN_PROGRESS` means the user came back unfinished, so keep waiting
- it is read on **two** signals: a `ContentObserver` on the status URI (which is
  what the API docs prescribe for it), and this app coming back to the
  foreground. The observer is the one that matters - Identity Guardian dismisses
  its lock screen to whatever the system puts next, which need not be this app,
  so an outcome only read on resume can be missed entirely. The foreground read
  is the backstop for a device that does not notify
- an **empty** status - a cursor whose extras carry no `RESULT` - means Identity
  Guardian has no lock screen action to report on, which is what it looks like
  once a flow is over and it has cleared the status. That is "nothing to say", so
  the phase is left alone. Treating it as a failure is what showed
  `Authentication failed: Status query did not contain a "RESULT" value` to a
  user who had signed in perfectly well
- reading back *who* signed in stays lead-app's job

## Signing the previous user out first

- Identity Guardian will not put its lock screen up for a user it has already
  authenticated, so **Logout User runs before every Start Authentication**.
  Without it the second run of the demo answered and no lock screen appeared,
  leaving the screen waiting on a user who had nothing to authenticate on
- it is best-effort and its result is ignored: there is no session to end on the
  first run, and the docs note the API only applies while Proxy Mode is inactive.
  Neither is a reason not to try authenticating

## Allow Caller to Call Service

- sample code to demo Zebra API usage: https://github.com/ys2714/zebra-sdk-kotlin-wrapper
- please refer to the code at: https://github.com/ys2714/zebra-sdk-kotlin-wrapper/blob/main/emdk_kotlin_wrapper/src/main/java/com/zebra/emdk_kotlin_wrapper/mx/MXProfileProcessor%2BAccessManager.kt the method name: "internal fun MXProfileProcessor.callAccessManagerAllowCallService()"
- the delegation scopes needed are the Start Authentication URI, the Get
  Authentication Status URI, and Logout User - the last under both spellings,
  because the docs give its content URI as `currentsession` while it is invoked
  as a `lockscreenaction` like the other two. An unused scope is harmless, a
  missing one is not
- this runs **only** when Identity Guardian answers `Caller is unauthorized`, not
  up front, and at most **once automatically** per app run - a second automatic
  attempt would change nothing (it is idempotent) and only cost the timeout. The
  retry button is there for a deliberate second go. The MX step depends on `com.symbol.mxmf`, which can stop answering
  submissions entirely (EMDK logs "Submitting XML to MXMF" and goes quiet), so it
  must never be a gate in front of an API that would have worked
- EMDK timeouts are deliberately short (8s open / 10s per profile): the step is
  best-effort, so failing fast beats making the user wait

## Release Gradle Task

- build the apk and rename it into "zebra-ig-crew-demo-<tag>"
- code sign the apk use release key "zebra-ig-demo-key"
- copy the apk to the root folder of this project
