# Zebra Identity Guardian Demo

## Overview

- identity guardian API URL: https://techdocs.zebra.com/identityguardian/3-1/api/
- use kotlin language
- use jetpack compose
- add comments to the source code
- simple feature for demostrate identity guardian content provider based API.

## UI

- Button1: Start Authentication
- Button2: Get Current User Session

## Allow Caller to Call Service

- sample code to demo Zebra API usage: https://github.com/ys2714/zebra-sdk-kotlin-wrapper
- please refer to the code at: https://github.com/ys2714/zebra-sdk-kotlin-wrapper/blob/main/emdk_kotlin_wrapper/src/main/java/com/zebra/emdk_kotlin_wrapper/mx/MXProfileProcessor%2BAccessManager.kt the method name: "internal fun MXProfileProcessor.callAccessManagerAllowCallService()"

## Release Gradle Task

- build the apk and rename it into "zebra-ig-demo-<tag>"
- code sign the apk use release key "zebra-ig-demo-key"
- copy the apk to the root folder of this project