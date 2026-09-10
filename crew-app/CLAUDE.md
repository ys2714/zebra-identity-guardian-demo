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
- authentication starts automatically as soon as the delegation scopes are in
  place, so opening the app is enough; the button is for re-running it
- the call uses Verification 3, i.e. `user_verification=authenticationScheme3`, with
  `launchflag=blocking`
- while the lock screen is up the screen says it is waiting for the user on it,
  and reports no outcome

## Start Authentication vs Get Authentication Status

- Start Authentication returns as soon as Identity Guardian accepts the request,
  so its `RESULT` only describes the *launch* of the lock screen (normally
  `IN_PROGRESS`) and must never be shown as the authentication outcome
- `BUSY` / `ERROR` from the launch mean the lock screen never came up, so those
  are terminal; anything else means the user is now on the lock screen
- Get Authentication Status is what reports the outcome; it is queried when this
  app is resumed, which with `launchflag=blocking` is when the lock screen is out
  of the way. `SUCCESS` is the success message; `IN_PROGRESS` means the user came
  back unfinished, so keep waiting
- reading back *who* signed in stays lead-app's job

## Allow Caller to Call Service

- sample code to demo Zebra API usage: https://github.com/ys2714/zebra-sdk-kotlin-wrapper
- please refer to the code at: https://github.com/ys2714/zebra-sdk-kotlin-wrapper/blob/main/emdk_kotlin_wrapper/src/main/java/com/zebra/emdk_kotlin_wrapper/mx/MXProfileProcessor%2BAccessManager.kt the method name: "internal fun MXProfileProcessor.callAccessManagerAllowCallService()"
- two delegation scopes are needed: the Start Authentication URI and the Get
  Authentication Status URI

## Release Gradle Task

- build the apk and rename it into "zebra-ig-crew-demo-<tag>"
- code sign the apk use release key "zebra-ig-demo-key"
- copy the apk to the root folder of this project
