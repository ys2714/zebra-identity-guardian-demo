package com.zebra.iglead.mx

import android.content.Context

/**
 * MX AccessMgr calls, i.e. "who is allowed to call which service on this device".
 *
 * Reference: https://techdocs.zebra.com/mx/accessmgr/
 *
 * Port of `MXProfileProcessor.callAccessManagerAllowCallService()` /
 * `getCallServicePermission()` from
 * https://github.com/ys2714/zebra-sdk-kotlin-wrapper
 */
class AccessManager(
    context: Context,
    private val processor: EmdkProfileProcessor = EmdkProfileProcessor(context),
) {

    private val appContext: Context = context.applicationContext

    /**
     * Allowlists this app as a caller of every service in [serviceIdentifiers]
     * (AccessMgr ServiceAccessAction 4, "AllowCaller").
     *
     * Doing this from the app itself is what StageNow would otherwise do at
     * staging time; the caller package and signature are read from this APK.
     *
     * @throws MxException when EMDK is unavailable or MX rejects a profile.
     */
    suspend fun allowCallService(serviceIdentifiers: List<String>) {
        allowCallService(
            serviceIdentifiers = serviceIdentifiers,
            callerPackageName = appContext.packageName,
            callerSignature = PackageSignature.base64(appContext),
        )
    }

    /** Allowlists an arbitrary caller; see the other overload. */
    suspend fun allowCallService(
        serviceIdentifiers: List<String>,
        callerPackageName: String,
        callerSignature: String,
    ) {
        require(serviceIdentifiers.isNotEmpty()) { "No service identifiers to allowlist." }

        // One profile per service: MX stores `ServiceIdentifier` verbatim rather
        // than splitting it, so a comma-separated list only ever allowlists a
        // service literally named "a,b,c".
        serviceIdentifiers.forEach { serviceIdentifier ->
            val profileXml = MxProfileTemplate.read(
                context = appContext,
                assetName = PROFILE_ASSET,
                params = mapOf(
                    PARM_SERVICE_IDENTIFIER to serviceIdentifier,
                    PARM_CALLER_PACKAGE_NAME to callerPackageName,
                    PARM_CALLER_SIGNATURE to callerSignature,
                ),
            )

            processor.applyProfile(PROFILE_NAME, profileXml)
        }
    }

    /** Releases the underlying EMDK session. */
    fun release() = processor.release()

    private companion object {
        const val PROFILE_ASSET = "profile_access_manager_allow_call_service.xml"

        /** Must match the ProfileName parm inside [PROFILE_ASSET]. */
        const val PROFILE_NAME = "AccessManagerAllowCallService"

        const val PARM_SERVICE_IDENTIFIER = "ServiceIdentifier"
        const val PARM_CALLER_PACKAGE_NAME = "CallerPackageName"
        const val PARM_CALLER_SIGNATURE = "CallerSignature"
    }
}
