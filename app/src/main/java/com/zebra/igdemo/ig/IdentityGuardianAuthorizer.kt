package com.zebra.igdemo.ig

import com.zebra.igdemo.mx.AccessManager
import com.zebra.igdemo.zdm.ZdmDelegation
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
class IdentityGuardianAuthorizer(
    private val accessManager: AccessManager,
    private val delegation: ZdmDelegation,
    private val serviceIdentifiers: List<String> =
        IdentityGuardianContract.DELEGATION_SCOPES,
) {

    /**
     * Authorizes this app for every Identity Guardian API the demo calls.
     *
     * Both steps are idempotent, so this is safe to run on every launch.
     */
    suspend fun authorize(): Result<Unit> = try {
        // The StageNow walkthrough repeats an AccessMgr profile per API URI; one
        // profile with all of them in ServiceIdentifier does the same in a pass.
        accessManager.allowCallService(serviceIdentifiers)

        // ZDM issues a token per scope, so this part is per URI. The token itself
        // is only needed by intent-based Zebra APIs - Identity Guardian reads the
        // delegation from ZDM - but acquiring it is what creates the delegation.
        serviceIdentifiers.forEach { serviceIdentifier ->
            delegation.acquireToken(serviceIdentifier)
        }

        Result.success(Unit)
    } catch (e: CancellationException) {
        // The caller's scope is going away; that is not an authorization failure.
        throw e
    } catch (e: Exception) {
        // Anything EMDK, MX or ZDM throws is reported in the UI rather than
        // crashing; the APIs may still work from an admin-staged allowlist.
        Result.failure(e)
    }

    /** Releases the EMDK session held for applying the profile. */
    fun release() = accessManager.release()
}
