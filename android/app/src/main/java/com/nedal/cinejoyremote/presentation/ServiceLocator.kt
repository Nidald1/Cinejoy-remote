package com.nedal.cinejoyremote.presentation

import com.nedal.cinejoyremote.core.SupabaseProvider
import com.nedal.cinejoyremote.data.SupabaseAuthRepository
import com.nedal.cinejoyremote.data.SupabaseMirrorRepository
import com.nedal.cinejoyremote.domain.SendCommandUseCase
import com.nedal.cinejoyremote.domain.SignInUseCase
import com.nedal.cinejoyremote.domain.SignUpUseCase

// Manual DI: the app is small enough that Koin/Hilt would be pure overhead.
object ServiceLocator {
    private val client get() = SupabaseProvider.client

    val authRepository by lazy { SupabaseAuthRepository(client) }
    val mirrorRepository by lazy { SupabaseMirrorRepository(client) }

    val signIn by lazy { SignInUseCase(authRepository) }
    val signUp by lazy { SignUpUseCase(authRepository) }
    val sendCommand by lazy { SendCommandUseCase(mirrorRepository) }
}
