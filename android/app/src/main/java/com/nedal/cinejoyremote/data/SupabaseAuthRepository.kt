package com.nedal.cinejoyremote.data

import com.nedal.cinejoyremote.domain.AuthRepository
import com.nedal.cinejoyremote.domain.AuthState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupabaseAuthRepository(private val client: SupabaseClient) : AuthRepository {

    override val authState: Flow<AuthState> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> AuthState.SignedIn(status.session.user?.email)
            else -> AuthState.SignedOut
        }
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    // Returns true when the account still needs email confirmation.
    override suspend fun signUp(email: String, password: String): Result<Boolean> = runCatching {
        val result = client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        result != null
    }

    override suspend fun signOut() {
        runCatching { client.auth.signOut() }
    }
}
