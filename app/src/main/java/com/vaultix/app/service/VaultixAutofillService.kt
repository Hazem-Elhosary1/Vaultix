package com.vaultix.app.service

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.text.InputType
import android.widget.RemoteViews
import com.vaultix.app.R
import com.vaultix.app.data.local.dao.PasswordDao
import com.vaultix.app.data.local.entity.PasswordEntity
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import com.vaultix.app.security.SecurePreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class VaultixAutofillService : AutofillService() {

    @Inject lateinit var passwordDao: PasswordDao
    @Inject lateinit var securePreferences: SecurePreferences
    @Inject lateinit var cryptoManager: CryptoManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.last().structure
        val packageName = structure.activityComponent.packageName
        
        serviceScope.launch {
            try {
                val responseBuilder = FillResponse.Builder()
                val fields = findAutofillFields(structure)
                
                if (fields.usernameId == null && fields.passwordId == null) {
                    callback.onSuccess(null)
                    return@launch
                }

                val key = KeystoreManager.getOrCreateDatabaseKey()
                val friendlyName = packageName.split(".").lastOrNull { it != "android" && it != "com" && it != "app" } ?: packageName
                
                // Try to extract domain if it's a browser
                val browserDomain = extractWebDomain(structure)
                Log.d("VaultixAutofill", "Detected browser domain: $browserDomain")

                // Fetch all potential candidates for this package OR general web items
                val allPasswords = passwordDao.getPasswordsByMatch(packageName)
                
                // Broad search segments
                val searchSegments = mutableSetOf<String>()
                searchSegments.add(packageName.lowercase())
                browserDomain?.let { searchSegments.add(it.lowercase()) }
                packageName.split(".").filter { it.length > 3 && it != "com" && it != "android" }.forEach { searchSegments.add(it.lowercase()) }

                val matches = allPasswords.filter { entity ->
                    val isSameVault = entity.isFake == com.vaultix.app.security.VaultSession.isFakeVaultActive
                    if (!isSameVault) return@filter false
                    
                    val decryptedTitle = runCatching { cryptoManager.decrypt(entity.title, key) }.getOrDefault("").lowercase()
                    val decryptedWebsite = runCatching { cryptoManager.decrypt(entity.website, key) }.getOrDefault("").lowercase()
                    
                    val pkgMatch = entity.appPackageName == packageName
                    val domainMatch = browserDomain != null && (decryptedWebsite.contains(browserDomain) || decryptedTitle.contains(browserDomain))
                    val segmentMatch = searchSegments.any { segment -> 
                        decryptedTitle.contains(segment) || decryptedWebsite.contains(segment) 
                    }

                    pkgMatch || domainMatch || segmentMatch
                }
                
                Log.d("VaultixAutofill", "Final matches found: ${matches.size} for $packageName / $browserDomain")
                
                Log.d("VaultixAutofill", "Found ${matches.size} matches for $packageName (friendly: $friendlyName)")
                
                // 1. Add Suggestions
                if (matches.isNotEmpty()) {
                    matches.forEach { match ->
                        val dataset = buildDataset(match, fields)
                        if (dataset != null) {
                            responseBuilder.addDataset(dataset)
                        }
                    }
                }

                // 2. Setup SaveInfo - Include fallbacks to ensure prompt visibility
                val saveFields = mutableListOf<AutofillId>()
                fields.passwordId?.let { saveFields.add(it) }
                fields.usernameId?.let { saveFields.add(it) }

                if (saveFields.isNotEmpty()) {
                    val saveInfo = SaveInfo.Builder(
                        SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME,
                        saveFields.toTypedArray()
                    ).setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                     .build()
                    responseBuilder.setSaveInfo(saveInfo)
                }

                callback.onSuccess(responseBuilder.build())
            } catch (e: Exception) {
                Log.e("VaultixAutofill", "Error processing fill request", e)
                callback.onFailure(e.message)
            }
        }
    }

    private fun buildDataset(password: PasswordEntity, fields: AutofillFields): Dataset? {
        if (fields.usernameId == null && fields.passwordId == null) return null

        val key = KeystoreManager.getOrCreateDatabaseKey()
        val title = runCatching { cryptoManager.decrypt(password.title, key) }.getOrDefault("Vaultix Item")
        val username = runCatching { cryptoManager.decrypt(password.username, key) }.getOrDefault("")

        val presentation = RemoteViews(packageName, R.layout.autofill_suggestion_item).apply {
            setTextViewText(R.id.text_title, "$title ($username)")
        }

        val authIntent = Intent(this, AutofillAuthActivity::class.java).apply {
            putExtra("password_id", password.id)
            putExtra("username_id", fields.usernameId)
            putExtra("password_id_id", fields.passwordId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 
            password.id.hashCode(), 
            authIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_MUTABLE
        )

        val datasetBuilder = Dataset.Builder()
        
        var valueSet = false
        // Bind the suggestion to both fields so it appears regardless of which one is focused
        fields.usernameId?.let {
            datasetBuilder.setValue(it, AutofillValue.forText(""), presentation)
            valueSet = true
        }
        fields.passwordId?.let {
            datasetBuilder.setValue(it, AutofillValue.forText(""), presentation)
            valueSet = true
        }

        return if (valueSet) {
            datasetBuilder.setAuthentication(pendingIntent.intentSender).build()
        } else null
    }

    private fun findAutofillFields(structure: AssistStructure): AutofillFields {
        val fields = AutofillFields()
        val nodes = mutableListOf<AssistStructure.ViewNode>()
        
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            addNodes(windowNode.rootViewNode, nodes)
        }

        for (node in nodes) {
            if (node.visibility != android.view.View.VISIBLE) continue
            
            val hints = node.autofillHints ?: emptyArray()
            val idEntry = node.idEntry?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val className = node.className?.lowercase() ?: ""
            val inputType = node.inputType
            val text = node.text?.toString()?.lowercase() ?: ""
            
            // CRITICAL: Exclude search fields to prevent annoying save prompts while searching
            val isSearchField = idEntry.contains("search") || idEntry.contains("query") || 
                               idEntry.contains("find") || idEntry.contains("بحث") ||
                               contentDesc.contains("search") || className.contains("search") ||
                               text.contains("search") || hints.any { it.contains("search", true) }
            
            if (isSearchField) {
                Log.d("VaultixAutofill", "Excluding search field: $idEntry")
                continue
            }
            
            val isPasswordField = (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                                 (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                                 (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                                 className.contains("password") || className.contains("pwd") ||
                                 hints.any { h -> h.contains("pass", true) || h.contains("pwd", true) } || 
                                 idEntry.contains("pass") || idEntry.contains("pwd") || idEntry.contains("secret") ||
                                 contentDesc.contains("password") || contentDesc.contains("secret") ||
                                 text.contains("كلمة") || text.contains("مرور") || text.contains("السر")
            
            val isUsernameField = !isPasswordField && (
                                 hints.any { h -> h.contains("user", true) || h.contains("email", true) || h.contains("login", true) } || 
                                 idEntry.contains("user") || idEntry.contains("email") || idEntry.contains("login") || 
                                 idEntry.contains("username") || idEntry.contains("id") ||
                                 contentDesc.contains("user") || contentDesc.contains("email") || contentDesc.contains("login") ||
                                 text.contains("البريد") || text.contains("اسم") || text.contains("مستخدم") || text.contains("دخول")
                                 )

            if (isUsernameField && fields.usernameId == null) {
                fields.usernameId = node.autofillId
                Log.d("VaultixAutofill", "Found potential username field: ID=$idEntry, Class=$className")
            } else if (isPasswordField && fields.passwordId == null) {
                fields.passwordId = node.autofillId
                Log.d("VaultixAutofill", "Found potential password field: ID=$idEntry, Class=$className")
            }
        }
        
        // Fallback: Use EditTexts if specific hints are missing
        if (fields.passwordId == null) {
            val editTexts = nodes.filter { node ->
                (node.visibility == android.view.View.VISIBLE) && (
                node.className?.contains("EditText", true) == true || 
                node.className?.contains("TextField", true) == true ||
                node.inputType != 0)
            }
            
            if (editTexts.isNotEmpty()) {
                // Usually password is the last one in a simple form, username is before it
                val lastEdit = editTexts.last()
                val prevEdit = if (editTexts.size > 1) editTexts[editTexts.size - 2] else null
                
                if (fields.passwordId == null) fields.passwordId = lastEdit.autofillId
                if (fields.usernameId == null) fields.usernameId = prevEdit?.autofillId
                Log.d("VaultixAutofill", "Using fallback EditTexts: last=${lastEdit.idEntry}, prev=${prevEdit?.idEntry}")
            }
        }
        
        return fields
    }

    private fun addNodes(node: AssistStructure.ViewNode, nodes: MutableList<AssistStructure.ViewNode>) {
        nodes.add(node)
        for (i in 0 until node.childCount) {
            addNodes(node.getChildAt(i), nodes)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        serviceScope.launch {
            try {
                val contexts = request.fillContexts
                val packageName = contexts.last().structure.activityComponent.packageName
                
                var username: String? = null
                var password: String? = null
                var detectedDomain: String? = null

                // Traverse contexts in reverse to find the latest available data
                for (context in contexts.reversed()) {
                    val structure = context.structure
                    val fields = findAutofillFields(structure)
                    
                    if (username == null) username = findTextForAutofillId(structure, fields.usernameId)
                    if (password == null) password = findTextForAutofillId(structure, fields.passwordId)
                    if (detectedDomain == null) detectedDomain = extractWebDomain(structure)
                    
                    if (username != null && password != null) break
                }
                
                Log.d("VaultixAutofill", "onSaveRequest: Final extracted username=${username?.take(3)}..., passwordLength=${password?.length ?: 0}, domain=$detectedDomain")

                // Only save if we have a password and it's not a search query
                if (!password.isNullOrBlank() && password.length > 3) {
                    val appLabel = try {
                        val pm = packageManager
                        val info = pm.getApplicationInfo(packageName, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (e: Exception) {
                        packageName
                    }
                    
                    val displayTitle = detectedDomain ?: appLabel
                    
                    withContext(Dispatchers.IO) {
                        val key = KeystoreManager.getOrCreateDatabaseKey()
                        val now = System.currentTimeMillis()
                        
                        val entity = PasswordEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            title = cryptoManager.encrypt(displayTitle, key),
                            username = cryptoManager.encrypt(username ?: "", key),
                            password = cryptoManager.encrypt(password, key),
                            website = cryptoManager.encrypt(detectedDomain ?: packageName, key),
                            appPackageName = packageName,
                            notes = cryptoManager.encrypt("Auto-saved from $appLabel", key),
                            passwordStrength = 0,
                            isFavorite = false,
                            passwordHistory = "",
                            createdAt = now,
                            updatedAt = now,
                            expiresAt = null,
                            keyVersion = 1,
                            isFake = com.vaultix.app.security.VaultSession.isFakeVaultActive
                        )
                        passwordDao.insertPassword(entity)
                    }
                }
                callback.onSuccess()
            } catch (e: Exception) {
                Log.e("VaultixAutofill", "Error saving credentials", e)
                callback.onFailure(e.message)
            }
        }
    }

    private fun findTextForAutofillId(structure: AssistStructure, target: AutofillId?): String? {
        if (target == null) return null
        val nodes = mutableListOf<AssistStructure.ViewNode>()
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            addNodes(windowNode.rootViewNode, nodes)
        }

        for (node in nodes) {
            if (node.autofillId == target) {
                val text = node.text?.toString()
                if (!text.isNullOrEmpty()) return text
                return node.autofillValue?.textValue?.toString()
            }
        }
        return null
    }

    private fun extractWebDomain(structure: AssistStructure): String? {
        val nodes = mutableListOf<AssistStructure.ViewNode>()
        for (i in 0 until structure.windowNodeCount) {
            addNodes(structure.getWindowNodeAt(i).rootViewNode, nodes)
        }

        for (node in nodes) {
            // Priority 1: Official webDomain field (most accurate for browsers)
            val webDomain = node.webDomain
            if (!webDomain.isNullOrBlank()) {
                val cleanDomain = webDomain.replace(Regex("https?://(www\\.)?"), "").split("/").first()
                if (cleanDomain.contains(".")) return cleanDomain
            }

            // Priority 2: Check for URL identifiers in node IDs (common in browser address bars)
            val idEntry = node.idEntry?.lowercase() ?: ""
            if (idEntry.contains("url") || idEntry.contains("address") || idEntry.contains("location")) {
                val urlText = node.text?.toString() ?: node.contentDescription?.toString()
                if (!urlText.isNullOrBlank()) {
                    val domain = urlText.replace(Regex("https?://(www\\.)?"), "").split("/").first().split(" ").first()
                    if (domain.contains(".") && domain.length > 3) return domain
                }
            }

            // Priority 3: Fallback - search for any text that looks like a URL/Domain
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            if (text.startsWith("http") || text.contains("://")) {
                try {
                    val domain = text.replace(Regex("https?://(www\\.)?"), "").split("/").first().split(" ").first()
                    if (domain.contains(".") && domain.length > 3) return domain
                } catch (e: Exception) {}
            }
        }
        return null
    }

    private class AutofillFields {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
    }
}
