package com.vaultix.app.security

/**
 * Manages the current active session state (Real vs Fake Vault).
 * This ensures that repositories only read/write data associated with the current session.
 */
object VaultSession {
    var isFakeVaultActive: Boolean = false
    var isAuthenticated: Boolean = false
}
