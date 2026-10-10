package com.example.auth

/**
 * A comprehensive sealed class representing the various states of Authentication.
 * This structure prevents the app from flashing login screens and provides precise 
 * state feedback to the UI (e.g., specific error messages).
 */
sealed class AuthState {
    // Initial state before we know if the user is logged in
    object Idle : AuthState()
    
    // In-progress state for login, sign-up, or session restoration
    object Loading : AuthState()
    
    // Successfully authenticated
    object Success : AuthState()

    // --- Specific Error States ---
    data class InvalidCredentials(val message: String = "Incorrect email or password. Please try again.") : AuthState()
    
    data class EmailAlreadyRegistered(val message: String = "An account with this email already exists.") : AuthState()
    
    data class WeakPassword(val message: String = "Password must be at least 8 characters with a mix of letters and numbers.") : AuthState()
    
    data class NetworkFailure(val message: String = "Network error. Please check your connection and try again.") : AuthState()
    
    data class SupabaseError(val message: String) : AuthState() // Catch-all for other Supabase exceptions
    
    // --- Flow States ---
    object PasswordResetSent : AuthState()
    
    object GoogleSignInCancelled : AuthState()
    
    data class GoogleSignInFailed(val message: String) : AuthState()
}
