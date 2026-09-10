package com.zebra.igcrew.ig

import com.zebra.igcrew.mx.AccessManager
import com.zebra.igcrew.zdm.ZdmDelegation
import kotlinx.coroutines.CancellationException

/**
 * Gets this app authorized to call the Identity Guardian APIs.
 *
 * Identity Guardian does not check an Android permission - its provider asks
 * Zebra Device Manager whether the caller holds a *delegation scope* for the API
 * URI being called, and answers "Caller is unauthorized" when it does not
 * (Identity Guardian logs "Delegation scoped is not granted"). Granting that
 * scope takes two steps, both of which this class performs:
 *
 *  1. an MX AccessMgr profile that allows this package + signature to call the
 *     service named by the URI ([AccessManager]), and
 *  2. acquiring a ZDM delegation token for the same URI ([ZdmDelegation]), which
 *     is what records the delegation Identity Guardian then finds.
 *
 * References:
 * - https://techdocs.zebra.com/identityguardian/3-1/api/
 * - https://techdocs.zebra.com/mx/accessmgr/
 */
/** Neither step got this app a delegation scope. */
class AuthorizationException(message: String) : Exception(message)

class IdentityGuardianAuthorizer(
    private val accessManager: AccessManager,
    private val delegation: ZdmDelegation,
    private val serviceIdentifiers: List<String> =
        IdentityGuardianContract.DELEGATION_SCOPES,
) {

    /**
     * Authorizes this app for every Identity Guardian API the demo calls.
     *
     * The two steps are attempted independently, and that matters. Identity
     * Guardian checks step 2 - the ZDM delegation - so skipping it because step
     * 1 reported a problem throws away the only step that records anything. Step
     * 1 can fail for reasons that say nothing about whether step 2 will work: an
     * administrator may have staged the same AccessMgr profile already, or the
     * device's MX framework service may be refusing to answer submissions at
     * all, which is a device fault rather than a verdict on this app.
     *
     * Succeeds if any scope came back with a token, since one landed scope is
     * enough for the API that needs it.
     *
     * Both steps are idempotent, so this is safe to run more than once.
     */
    suspend fun authorize(): Result<Unit> {
        val failures = mutableListOf<String>()

        // Step 1, best-effort: allow this package + signature to call the
        // services named by the URIs. The StageNow walkthrough repeats a profile
        // per API URI; one profile with all of them does the same in a pass.
        try {
            accessManager.allowCallService(serviceIdentifiers)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures += e.message ?: e::class.java.simpleName
        }

        // Step 2, always attempted: ZDM issues a token per scope, and acquiring
        // it is what creates the delegation Identity Guardian goes looking for.
        var granted = false
        serviceIdentifiers.forEach { serviceIdentifier ->
            try {
                delegation.acquireToken(serviceIdentifier)
                granted = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += e.message ?: e::class.java.simpleName
            }
        }

        return if (granted) {
            Result.success(Unit)
        } else {
            Result.failure(AuthorizationException(failures.joinToString(" / ")))
        }
    }

    /** Releases the EMDK session held for applying the profile. */
    fun release() = accessManager.release()
}
