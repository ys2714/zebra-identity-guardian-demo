package com.zebra.iglead.zdm

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Acquiring a Zebra Device Manager delegation token failed. */
class ZdmException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Zebra Device Manager (ZDM) delegation scopes.
 *
 * Zebra's protected APIs are gated by a "delegation scope" held in ZDM, and
 * granting one takes two steps:
 *
 *  1. an MX AccessMgr profile allows this app to ask for the scope
 *     (see [com.zebra.iglead.mx.AccessManager]), and
 *  2. the app acquires a token for that scope from the ZDM content provider,
 *     which is what actually records the delegation.
 *
 * Identity Guardian checks step 2: without it its provider answers
 * "Caller is unauthorized" and logs "Delegation scoped is not granted", even
 * though MX reports the AccessMgr profile as applied.
 */
class ZdmDelegation(
    private val contentResolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Acquires a delegation token for [scope], recording the delegation in ZDM.
     *
     * @return the token, which callers of intent-based Zebra APIs have to pass
     * along. Identity Guardian reads the delegation from ZDM itself, so the
     * demo only needs the call to have happened.
     * @throws ZdmException when ZDM refuses or returns nothing.
     */
    suspend fun acquireToken(scope: String): String = withContext(dispatcher) {
        val token = try {
            contentResolver.query(
                ACQUIRE_TOKEN_URI,
                /* projection = */ null,
                /* selection = */ "$SELECTION_DELEGATION_SCOPE=?",
                /* selectionArgs = */ arrayOf(scope),
                /* sortOrder = */ null,
            ).use { cursor ->
                val rows = cursor ?: throw ZdmException(
                    "ZDM returned no cursor for $scope."
                )
                if (!rows.moveToFirst()) {
                    throw ZdmException("ZDM returned no token row for $scope.")
                }
                val column = rows.getColumnIndex(COLUMN_QUERY_RESULT)
                if (column < 0) {
                    throw ZdmException("ZDM response has no \"$COLUMN_QUERY_RESULT\" column.")
                }
                rows.getString(column)
            }
        } catch (e: SecurityException) {
            // ZDM rejects callers that MX has not allowlisted for this scope.
            throw ZdmException(
                "ZDM rejected this app as a caller for $scope. " +
                    "The MX AccessMgr profile for that scope has to be applied first.",
                e,
            )
        } catch (e: IllegalArgumentException) {
            throw ZdmException("The ZDM content provider is unavailable: ${e.message}", e)
        }

        if (token.isNullOrBlank()) {
            throw ZdmException("ZDM returned an empty token for $scope.")
        }
        token
    }

    private companion object {
        /** Zebra Device Manager's content provider. */
        val AUTHORITY_URI: Uri = Uri.parse("content://com.zebra.devicemanager.zdmcontentprovider")

        /** Path that issues a token for a delegation scope. */
        val ACQUIRE_TOKEN_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "AcquireToken")

        /** The scope goes in the selection, as `delegation_scope=?`. */
        const val SELECTION_DELEGATION_SCOPE = "delegation_scope"

        /** Column holding the issued token. */
        const val COLUMN_QUERY_RESULT = "query_result"
    }
}
