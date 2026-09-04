package com.zebra.igdemo

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zebra.igdemo.ig.AuthenticationScheme
import com.zebra.igdemo.ig.IdentityGuardianClient
import com.zebra.igdemo.ig.IdentityGuardianException
import com.zebra.igdemo.ig.LaunchFlag
import com.zebra.igdemo.ig.SessionField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which API the last result came from, used as the result card's heading. */
enum class IdentityGuardianApi(val displayName: String) {
    START_AUTHENTICATION("Start Authentication"),
    GET_CURRENT_SESSION("Get Current User Session"),
}

/** Outcome of the most recent API call. */
sealed interface ApiResult {

    /** The API name this result belongs to. */
    val api: IdentityGuardianApi

    /**
     * The call succeeded.
     *
     * @param status short status line, e.g. the `RESULT` value of Start Authentication.
     * @param fields key/value detail rows to render, empty when there is nothing to show.
     * @param rawResponse the untouched provider payload, or null when there was none.
     */
    data class Success(
        override val api: IdentityGuardianApi,
        val status: String,
        val fields: List<SessionField> = emptyList(),
        val rawResponse: String? = null,
    ) : ApiResult

    /** The call failed; [hint] carries the suggested fix when one is known. */
    data class Failure(
        override val api: IdentityGuardianApi,
        val message: String,
        val hint: String? = null,
    ) : ApiResult
}

/** State rendered by the demo screen. */
data class MainUiState(
    val isBusy: Boolean = false,
    val result: ApiResult? = null,
)

/**
 * Drives the two demo actions and exposes their outcome as [MainUiState].
 *
 * The provider calls are suspending, so a call in flight simply flips [MainUiState.isBusy]
 * and the buttons disable themselves until it returns.
 */
class MainViewModel(
    private val client: IdentityGuardianClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /**
     * Calls the Start Authentication API, which brings up the Identity Guardian
     * lock screen. Defaults to the first authentication scheme in blocking mode.
     */
    fun startAuthentication(
        scheme: AuthenticationScheme = AuthenticationScheme.SCHEME_1,
        launchFlag: LaunchFlag = LaunchFlag.BLOCKING,
    ) = runApiCall(IdentityGuardianApi.START_AUTHENTICATION) {
        client.startAuthentication(scheme, launchFlag).map { authState ->
            ApiResult.Success(
                api = IdentityGuardianApi.START_AUTHENTICATION,
                status = authState,
                fields = listOf(
                    SessionField("user_verification", "Scheme", scheme.value),
                    SessionField("launchflag", "Launch Flag", launchFlag.value),
                ),
            )
        }
    }

    /** Calls the Get Current User Session (v2) API. */
    fun getCurrentUserSession() = runApiCall(IdentityGuardianApi.GET_CURRENT_SESSION) {
        client.getCurrentUserSession().map { session ->
            ApiResult.Success(
                api = IdentityGuardianApi.GET_CURRENT_SESSION,
                status = if (session.isEmpty) "NO ACTIVE SESSION" else "SESSION FOUND",
                fields = session.fields,
                rawResponse = session.rawJson.takeIf { it.isNotBlank() },
            )
        }
    }

    /** Shared plumbing: clear the previous result, show progress, map failures. */
    private fun runApiCall(
        api: IdentityGuardianApi,
        call: suspend () -> Result<ApiResult.Success>,
    ) {
        // Ignore taps while a call is already in flight.
        if (_uiState.value.isBusy) return

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, result = null) }

            val result = call().fold(
                onSuccess = { it },
                onFailure = { error ->
                    ApiResult.Failure(
                        api = api,
                        message = error.message ?: "Unknown error",
                        hint = (error as? IdentityGuardianException)?.hint,
                    )
                },
            )

            _uiState.update { it.copy(isBusy = false, result = result) }
        }
    }

    companion object {
        /** Supplies the [IdentityGuardianClient] with the application ContentResolver. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application: Application = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                )
                MainViewModel(IdentityGuardianClient(application.contentResolver))
            }
        }
    }
}
