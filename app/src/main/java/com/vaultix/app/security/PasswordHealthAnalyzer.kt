package com.vaultix.app.security

import com.vaultix.app.data.model.Password
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordHealthAnalyzer @Inject constructor() {

    fun analyze(passwords: List<Password>): PasswordHealthReport {
        val totalCount = passwords.size
        if (totalCount == 0) return PasswordHealthReport()

        val weakPasswords = mutableListOf<Password>()
        val duplicatePasswords = mutableListOf<Password>()
        val oldPasswords = mutableListOf<Password>()
        
        // Group by content to find duplicates (convert to String for grouping key)
        val passwordGroups = passwords.groupBy { it.password.concatToString() }
        
        val ninetyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -90) }.timeInMillis

        passwords.forEach { pwd ->
            // Check Strength (Weak if < 60)
            if (calculateStrength(pwd.password) < 60) {
                weakPasswords.add(pwd)
            }

            // Check Duplicates (compare using contentToString for Map lookup)
            if (passwordGroups[pwd.password.concatToString()]?.size ?: 0 > 1) {
                duplicatePasswords.add(pwd)
            }

            // Check Age (Old if > 90 days)
            if (pwd.updatedAt < ninetyDaysAgo) {
                oldPasswords.add(pwd)
            }
        }

        val score = calculateOverallScore(totalCount, weakPasswords.size, duplicatePasswords.size, oldPasswords.size)

        return PasswordHealthReport(
            overallScore = score,
            weakCount = weakPasswords.size,
            duplicateCount = duplicatePasswords.size,
            oldCount = oldPasswords.size,
            weakItems = weakPasswords,
            duplicateItems = duplicatePasswords,
            oldItems = oldPasswords
        )
    }

    private fun calculateStrength(password: CharArray): Int {
        var score = 0
        if (password.size >= 8) score += 20
        if (password.size >= 12) score += 20
        if (password.any { it.isUpperCase() }) score += 15
        if (password.any { it.isLowerCase() }) score += 15
        if (password.any { it.isDigit() }) score += 15
        if (password.any { !it.isLetterOrDigit() }) score += 15
        return score.coerceAtMost(100)
    }

    private fun calculateOverallScore(total: Int, weak: Int, dup: Int, old: Int): Int {
        if (total == 0) return 100
        val deductions = (weak * 10) + (dup * 15) + (old * 5)
        val finalScore = (100 - (deductions.toFloat() / total)).toInt()
        return finalScore.coerceIn(0, 100)
    }
}

data class PasswordHealthReport(
    val overallScore: Int = 100,
    val weakCount: Int = 0,
    val duplicateCount: Int = 0,
    val oldCount: Int = 0,
    val weakItems: List<Password> = emptyList(),
    val duplicateItems: List<Password> = emptyList(),
    val oldItems: List<Password> = emptyList()
)
