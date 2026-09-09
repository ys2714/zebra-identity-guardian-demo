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

- one button: Start Authentication
- the call uses Verification 3, i.e. `user_verification=authenticationScheme3`, with
  `launchflag=blocking`
- when Identity Guardian answers `SUCCESS`, show the success message; any other
  `RESULT` (IN_PROGRESS / BUSY / ERROR) or a failed call is reported instead

## Allow Caller to Call Service

- sample code to demo Zebra API usage: https://github.com/ys2714/zebra-sdk-kotlin-wrapper
- please refer to the code at: https://github.com/ys2714/zebra-sdk-kotlin-wrapper/blob/main/emdk_kotlin_wrapper/src/main/java/com/zebra/emdk_kotlin_wrapper/mx/MXProfileProcessor%2BAccessManager.kt the method name: "internal fun MXProfileProcessor.callAccessManagerAllowCallService()"
- only one delegation scope is needed here: the Start Authentication URI

## Release Gradle Task

- build the apk and rename it into "zebra-ig-crew-demo-<tag>"
- code sign the apk use release key "zebra-ig-demo-key"
- copy the apk to the root folder of this project
