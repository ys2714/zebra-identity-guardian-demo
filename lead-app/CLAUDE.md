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

- login form, laid out as in `../lead-app-UI-layout.png`: `User :` text field, `Password :` text field, `Role :` text, `Login` button
- the only Identity Guardian API used is Get Current User Session; it runs on start-up and fills in User and Role
- values that came from the session are shown in red
- Login itself is local to the demo: it checks the form is complete and reports who would be signed in

## Allow Caller to Call Service

- sample code to demo Zebra API usage: https://github.com/ys2714/zebra-sdk-kotlin-wrapper
- please refer to the code at: https://github.com/ys2714/zebra-sdk-kotlin-wrapper/blob/main/emdk_kotlin_wrapper/src/main/java/com/zebra/emdk_kotlin_wrapper/mx/MXProfileProcessor%2BAccessManager.kt the method name: "internal fun MXProfileProcessor.callAccessManagerAllowCallService()"

## Release Gradle Task

- build the apk and rename it into "zebra-ig-lead-demo-<tag>"
- code sign the apk use release key "zebra-ig-demo-key", shared with crew-app
- copy the apk to the root folder of this project
