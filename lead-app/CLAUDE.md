# Zebra Identity Guardian Lead Demo

## Overview

- identity guardian API URL: https://techdocs.zebra.com/identityguardian/3-1/api/
- use kotlin language
- use jetpack compose
- add comments to the source code
- simple feature for demostrate identity guardian content provider based API.
- pairs with `../crew-app`, which demos Start Authentication (Verification 3);
  this app installs alongside it (applicationId `com.zebra.iglead`)

## UI

- login form, laid out as in `../lead-app-UI-layout.png`: `User :` text field, `Password :` text field, `Role :` text, `Login` button, plus an `Exit` button that closes the app
- the only Identity Guardian API used is Get Current User Session; it runs on start-up **and again whenever the app returns to the foreground**, so a user who signed in through IG Crew while this app was backgrounded still shows up
- values that came from the session are shown in red
- Login itself is local to the demo: it checks the form is complete and reports who would be signed in

## Localization

- English in `values/`, Japanese in `values-ja/`; the app follows the device locale
- the `Label :` column width is a dimension resource (`form_label_width`), because
  full-width kana need more room than the English labels - 96dp in `values/`,
  120dp in `values-ja/`. A new label long enough to wrap means widening that,
  not shrinking the text
- role strings are never translated: they are shown exactly as Identity Guardian
  reported them, and `R.array.blocked_roles` is matched against that same value,
  so it stays out of `values-ja/`
- every `%1$s` must survive translation, or `stringResource` throws at runtime
- diagnostic text coming out of EMDK / MX / ZDM / the provider is not localized;
  only the wrapper around it is

## Reading the v2 session payload

- the app queries `v2/currentsession`, whose payload is camelCase and **nested**:
  the user and role are `userId` / `userRole` inside `userInformation`, not
  `user_id` / `user_role` at the top level. Looking only at the top level is why
  User and Role once came up blank
- parsing flattens the object into leaf values keyed by dotted path, and lookups
  match on the trailing name too, so both the v2 and the legacy spellings work
- `userLoggedInState` is what says whether anybody is signed in — v2 answers the
  query even when nobody is, so a payload that parses is not by itself a session

## Role gate

- this is a lead's app, so a session whose role is not supposed to have it gets a
  "not available for this role" screen instead of the login form
- it is a **deny list**, in `R.array.blocked_roles` (ships with `Parttimer`),
  matched against `user_role` case-insensitively and ignoring padding. Any role
  not named there — and a session carrying no role — reaches the form, so the demo
  still works on a device whose roles are configured differently
- the blocked screen names the role, so it is obvious what to add to or remove
  from that array
- the gate is enforced in the view model's `login()` as well, not only by hiding
  the form

## Allow Caller to Call Service

- sample code to demo Zebra API usage: https://github.com/ys2714/zebra-sdk-kotlin-wrapper
- please refer to the code at: https://github.com/ys2714/zebra-sdk-kotlin-wrapper/blob/main/emdk_kotlin_wrapper/src/main/java/com/zebra/emdk_kotlin_wrapper/mx/MXProfileProcessor%2BAccessManager.kt the method name: "internal fun MXProfileProcessor.callAccessManagerAllowCallService()"

## Release Gradle Task

- build the apk and rename it into "zebra-ig-lead-demo-<tag>"
- code sign the apk use release key "zebra-ig-demo-key", shared with crew-app
- copy the apk to the root folder of this project
