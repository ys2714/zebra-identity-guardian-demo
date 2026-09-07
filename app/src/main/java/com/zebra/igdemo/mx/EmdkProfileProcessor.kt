package com.zebra.igdemo.mx

import android.content.Context
import android.util.Xml
import com.symbol.emdk.EMDKBase
import com.symbol.emdk.EMDKManager
import com.symbol.emdk.EMDKResults
import com.symbol.emdk.ProfileManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.atomic.AtomicBoolean

/** An MX/EMDK operation failed. [message] is safe to show in the UI. */
class MxException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Applies MX profiles through EMDK's ProfileManager.
 *
 * EMDK is a shared library provided by the device, so everything here only works
 * on a Zebra device with EMDK installed; elsewhere [applyProfile] fails fast with
 * an [MxException] instead of hanging.
 *
 * Modelled on `MXProfileProcessor.processProfileWithCallback()` from
 * https://github.com/ys2714/zebra-sdk-kotlin-wrapper, reshaped as suspend
 * functions so callers can just await the outcome.
 */
class EmdkProfileProcessor(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // EMDK holds on to the context for the lifetime of the manager, so only the
    // application context is kept here.
    private val appContext: Context = context.applicationContext

    // MX applies one profile at a time; serialise submissions so a second call
    // cannot be rejected with PREVIOUS_REQUEST_IN_PROGRESS.
    private val submissionLock = Mutex()

    private var emdkManager: EMDKManager? = null
    private var profileManager: ProfileManager? = null

    /**
     * Sends [profileXml] to MX as profile [profileName], suspending until MX has
     * processed it.
     *
     * @throws MxException when EMDK is unavailable or MX rejects the profile.
     */
    suspend fun applyProfile(profileName: String, profileXml: String): Unit = withContext(dispatcher) {
        submissionLock.withLock {
            try {
                val manager = withMxTimeout(EMDK_OPEN_TIMEOUT_MS, "opening EMDK") { profileManager() }
                withMxTimeout(PROFILE_TIMEOUT_MS, "processing MX profile $profileName") {
                    processProfile(manager, profileName, profileXml)
                }
            } catch (e: LinkageError) {
                // The EMDK classes come from a device-provided shared library that
                // is declared optional, so on a non-Zebra device they simply cannot
                // be loaded. Report that instead of crashing.
                throw MxException("EMDK is not installed on this device.", e)
            }
        }
    }

    /** Hands the EMDK session back to the device. Safe to call more than once. */
    fun release() {
        profileManager = null
        emdkManager?.release()
        emdkManager = null
    }

    /** Returns the cached ProfileManager, opening an EMDK session on first use. */
    private suspend fun profileManager(): ProfileManager {
        profileManager?.let { return it }

        val manager = emdkManager ?: openEmdk().also { emdkManager = it }
        val feature = requestFeature(manager, EMDKManager.FEATURE_TYPE.PROFILE)
        val profiles = feature as? ProfileManager
            ?: throw MxException("EMDK returned no ProfileManager for this device.")

        return profiles.also { profileManager = it }
    }

    /** Opens the EMDK session; [EMDKManager.EMDKListener.onOpened] hands back the manager. */
    private suspend fun openEmdk(): EMDKManager = suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)

        val listener = object : EMDKManager.EMDKListener {
            override fun onOpened(manager: EMDKManager?) {
                if (!resumed.compareAndSet(false, true)) return
                if (manager == null) {
                    continuation.resumeWith(
                        Result.failure(MxException("EMDK opened without a manager instance."))
                    )
                } else {
                    continuation.resumeWith(Result.success(manager))
                }
            }

            override fun onClosed() {
                // The EMDK service went away (e.g. it is being updated); drop the
                // cached handles so the next call opens a fresh session.
                profileManager = null
                emdkManager = null
            }
        }

        // Returns synchronously: a non-SUCCESS code means onOpened will never fire.
        val results = EMDKManager.getEMDKManager(appContext, listener)
        if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS &&
            resumed.compareAndSet(false, true)
        ) {
            continuation.resumeWith(
                Result.failure(
                    MxException(
                        "EMDK is not available on this device (${results.statusCode}). " +
                            "MX allowlisting needs a Zebra device with EMDK installed."
                    )
                )
            )
        }
    }

    /** Asks EMDK for one feature manager, e.g. [EMDKManager.FEATURE_TYPE.PROFILE]. */
    private suspend fun requestFeature(
        manager: EMDKManager,
        feature: EMDKManager.FEATURE_TYPE,
    ): EMDKBase? = suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)

        val listener = object : EMDKManager.StatusListener {
            override fun onStatus(status: EMDKManager.StatusData?, base: EMDKBase?) {
                // One listener per request, but EMDK reports every feature it owns.
                if (status == null || status.featureType != feature) return
                if (!resumed.compareAndSet(false, true)) return
                continuation.resumeWith(Result.success(base))
            }
        }

        try {
            manager.getInstanceAsync(feature, listener)
        } catch (e: Exception) {
            if (resumed.compareAndSet(false, true)) {
                continuation.resumeWith(
                    Result.failure(MxException("EMDK could not provide $feature: ${e.message}", e))
                )
            }
        }
    }

    /**
     * Submits the profile and waits for the matching
     * [ProfileManager.DataListener] callback.
     */
    private suspend fun processProfile(
        manager: ProfileManager,
        profileName: String,
        profileXml: String,
    ): Unit = suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)

        val listener = object : ProfileManager.DataListener {
            override fun onData(data: ProfileManager.ResultData?) {
                // Callbacks for other profiles are none of this call's business.
                if (data != null && data.profileName != profileName) return
                if (!resumed.compareAndSet(false, true)) return
                manager.removeDataListener(this)
                continuation.resumeWith(data.toOutcome())
            }
        }

        continuation.invokeOnCancellation { manager.removeDataListener(listener) }
        manager.addDataListener(listener)

        // SET applies the profile; the payload is the profile XML itself.
        val submission = manager.processProfileAsync(
            profileName,
            ProfileManager.PROFILE_FLAG.SET,
            arrayOf(profileXml),
        )

        // PROCESSING is the normal path: the outcome arrives via onData above.
        // Any other code is already the outcome, so don't wait for a callback
        // that may never come.
        if (submission.statusCode != EMDKResults.STATUS_CODE.PROCESSING &&
            resumed.compareAndSet(false, true)
        ) {
            manager.removeDataListener(listener)
            continuation.resumeWith(submission.toOutcome(profileName))
        }
    }

    /** Turns an MX callback into success, or a failure carrying MX's own wording. */
    private fun ProfileManager.ResultData?.toOutcome(): Result<Unit> {
        val data = this ?: return Result.failure(MxException("MX returned an empty response."))
        return data.result.toOutcome(data.profileName)
    }

    /** Maps an MX status code to success or to a failure describing it. */
    private fun EMDKResults.toOutcome(profileName: String?): Result<Unit> =
        when (statusCode) {
            EMDKResults.STATUS_CODE.SUCCESS -> Result.success(Unit)
            // MX accepted the profile but wants the XML response inspected: it
            // reports per-parameter problems there rather than in the status code.
            EMDKResults.STATUS_CODE.CHECK_XML -> parseXmlError(statusString)
                ?.let { Result.failure(it) }
                ?: Result.success(Unit)

            else -> Result.failure(toMxException(profileName))
        }

    /** Reads the `parm-error` / `characteristic-error` MX reports in its response XML. */
    private fun parseXmlError(statusXml: String?): MxException? {
        if (statusXml.isNullOrBlank()) return null

        return try {
            val parser = Xml.newPullParser().apply { setInput(StringReader(statusXml)) }
            var event = parser.eventType
            var error: MxException? = null

            while (event != XmlPullParser.END_DOCUMENT && error == null) {
                if (event == XmlPullParser.START_TAG) {
                    error = when (parser.name) {
                        // MX describes the problem in "desc"; older MX versions
                        // put the offending value in "value" instead.
                        "parm-error" -> MxException(
                            "MX rejected parameter \"${parser.getAttributeValue(null, "name")}\": " +
                                (parser.getAttributeValue(null, "desc")
                                    ?: parser.getAttributeValue(null, "value")
                                    ?: "no detail given")
                        )

                        "characteristic-error" -> MxException(
                            "MX rejected \"${parser.getAttributeValue(null, "type")}\": " +
                                (parser.getAttributeValue(null, "desc") ?: "no detail given")
                        )

                        else -> null
                    }
                }
                if (error == null) event = parser.next()
            }
            error
        } catch (e: Exception) {
            MxException("Could not read the MX response: ${e.message}", e)
        }
    }

    /** Builds the user-facing failure from an MX status code plus its messages. */
    private fun EMDKResults.toMxException(profileName: String?): MxException {
        val detail = listOfNotNull(
            statusString?.takeIf { it.isNotBlank() },
            extendedStatusMessage?.takeIf { it.isNotBlank() },
        ).joinToString(separator = " - ")

        return MxException(
            "MX could not apply ${profileName ?: "the profile"} ($statusCode)" +
                if (detail.isEmpty()) "." else ": $detail"
        )
    }

    /** Reports a stalled EMDK call as an [MxException] rather than a cancellation. */
    private suspend fun <T> withMxTimeout(timeoutMs: Long, what: String, block: suspend () -> T): T =
        try {
            withTimeout(timeoutMs) { block() }
        } catch (e: TimeoutCancellationException) {
            throw MxException("Timed out $what after ${timeoutMs / 1000}s.", e)
        }

    private companion object {
        /** EMDK usually answers in well under a second; this is only a safety net. */
        const val EMDK_OPEN_TIMEOUT_MS = 15_000L
        const val PROFILE_TIMEOUT_MS = 30_000L
    }
}
