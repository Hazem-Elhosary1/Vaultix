package com.vaultix.app.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Setup : Screen("setup")
    object Backup : Screen("backup")
    object PasswordGenerator : Screen("password_generator")
    object GlobalSearch : Screen("global_search")
    object Lock : Screen("lock")
    object Home : Screen("home")
    object Settings : Screen("settings")

    object Category : Screen("category/{type}") {
        fun createRoute(type: String) = "category/$type"
    }

    object Detail : Screen("detail/{id}/{type}") {
        fun createRoute(id: String, type: String) = "detail/$id/$type"
    }

    object AddEdit : Screen("addedit/{type}?id={id}&mode={mode}") {
        fun createRoute(type: String, id: String? = null, mode: String? = null): String {
            val route = StringBuilder("addedit/$type")
            val queryParams = mutableListOf<String>()
            if (id != null) queryParams += "id=$id"
            if (mode != null) queryParams += "mode=$mode"
            if (queryParams.isNotEmpty()) {
                route.append('?').append(queryParams.joinToString("&"))
            }
            return route.toString()
        }
    }

    // ── New Feature Screens ──

    object CardScan : Screen("card_scan")

    object FileVault : Screen("file_vault")
    object SecurityAudit : Screen("security_audit")

    // Unified Backup Export & Import Wizards
    object BackupExport : Screen("backup_export")
    object BackupImport : Screen("backup_import")

    object IdentityEdit : Screen("identity_edit?id={id}") {
        fun createRoute(id: String? = null) =
            if (id != null) "identity_edit?id=$id" else "identity_edit"
    }

    object PdfViewer : Screen("pdf_viewer/{id}/{name}") {
        fun createRoute(id: String, name: String) = "pdf_viewer/$id/$name"
    }

    object ImageViewer : Screen("image_viewer/{filePath}") {
        fun createRoute(path: String) = "image_viewer/${java.net.URLEncoder.encode(path, "UTF-8")}"
    }

    object Premium : Screen("premium")

    // ── Dev-only Screen (develop branch) ──
    object Development : Screen("development")
}
