package com.bu.paperclip.util

import android.content.Context
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import kotlin.coroutines.cancellation.CancellationException

sealed class SavedLoginResult {
    data class Found(val email: String, val password: String) : SavedLoginResult()
    /** Nothing saved, the sheet was dismissed, or no provider is available. */
    data object None : SavedLoginResult()
}

/** Thin wrapper over the system password manager (Credential Manager). The app never stores passwords itself. */
object SavedLogin {
    suspend fun pick(context: Context): SavedLoginResult = try {
        val res = CredentialManager.create(context).getCredential(
            context,
            GetCredentialRequest(listOf(GetPasswordOption())),
        )
        (res.credential as? PasswordCredential)?.let { SavedLoginResult.Found(it.id, it.password) } ?: SavedLoginResult.None
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        SavedLoginResult.None
    }

    /** Asks the password manager to save the credential; failures and declines are ignored. */
    suspend fun offerToSave(context: Context, email: String, password: String) {
        try {
            CredentialManager.create(context).createCredential(context, CreatePasswordRequest(id = email, password = password))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
    }
}
