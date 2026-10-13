package com.nedal.cinejoyremote.core

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

// Anon/publishable key: public by design (RLS owner policies are the real
// security layer). Mirrors cinejoy-remote/supabase/project.json.
private const val SUPABASE_URL = "https://pghsfyhdhqxzwlrhuutg.supabase.co"
private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBnaHNmeWhkaHF4endscmh1dXRnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE4MzQxNjAsImV4cCI6MjEwNzQxMDE2MH0.RvPp5ZUNEjb_6bclI8Ira0BesQA7qbvRx2TBhf7t6S0"

object SupabaseProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY) {
            install(Auth) // autoLoad/autoSave session from storage by default
            install(Postgrest)
            install(Realtime)
        }
    }
}
