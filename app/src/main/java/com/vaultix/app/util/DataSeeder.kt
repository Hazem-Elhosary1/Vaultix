package com.vaultix.app.util

import com.vaultix.app.data.model.*
import com.vaultix.app.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility to seed the database with mock data for testing and development.
 */
@Singleton
class DataSeeder @Inject constructor(
    private val passwordRepository: PasswordRepository,
    private val cardRepository: CardRepository,
    private val noteRepository: NoteRepository,
    private val identityRepository: IdentityRepository
) {
    private fun generateId() = UUID.randomUUID().toString()
    private fun now() = System.currentTimeMillis()

    suspend fun seedAll() = withContext(Dispatchers.IO) {
        seedPasswords()
        seedCards()
        seedNotes()
        seedIdentities()
    }

    suspend fun seedPasswords() = withContext(Dispatchers.IO) {
        val passwords = listOf(
            Password(
                id = generateId(),
                title = "Google Account",
                username = "johndoe@gmail.com",
                password = "SecurePassword123!".toCharArray(),
                website = "https://google.com",
                notes = "Primary email account",
                passwordStrength = 4, // Very Strong
                isFavorite = true,
                createdAt = now(),
                updatedAt = now()
            ),
            Password(
                id = generateId(),
                title = "Netflix",
                username = "john_netflix",
                password = "Password987".toCharArray(),
                website = "https://netflix.com",
                notes = "Shared with family",
                passwordStrength = 2, // Fair
                isFavorite = false,
                createdAt = now(),
                updatedAt = now()
            ),
            Password(
                id = generateId(),
                title = "GitHub",
                username = "jdoe_dev",
                password = "GitGud-2024-Secret".toCharArray(),
                website = "https://github.com",
                notes = "Work and personal projects",
                passwordStrength = 4,
                isFavorite = true,
                createdAt = now(),
                updatedAt = now()
            ),
            Password(
                id = generateId(),
                title = "Banking App",
                username = "123456789",
                password = "BankPass_55".toCharArray(),
                website = "https://mybank.com",
                notes = "DO NOT SHARE",
                passwordStrength = 3,
                isFavorite = false,
                createdAt = now(),
                updatedAt = now()
            ),
            Password(
                id = generateId(),
                title = "Twitter / X",
                username = "@johndoe",
                password = "Short".toCharArray(),
                website = "https://twitter.com",
                notes = "",
                passwordStrength = 1,
                isFavorite = false,
                createdAt = now(),
                updatedAt = now()
            )
        )
        passwords.forEach { passwordRepository.insertPassword(it) }
    }

    suspend fun seedCards() = withContext(Dispatchers.IO) {
        val cards = listOf(
            Card(
                id = generateId(),
                cardName = "Main Credit Card",
                holderName = "John Doe",
                cardNumber = "4532111122223333",
                expiryMonth = "12",
                expiryYear = "28",
                cvv = "123",
                cardType = "Visa",
                notes = "Used for daily expenses",
                isFavorite = true,
                createdAt = now(),
                updatedAt = now()
            ),
            Card(
                id = generateId(),
                cardName = "Savings Debit",
                holderName = "John Doe",
                cardNumber = "5105105105105105",
                expiryMonth = "05",
                expiryYear = "27",
                cvv = "999",
                cardType = "MasterCard",
                notes = "HSBC Savings account",
                isFavorite = false,
                createdAt = now(),
                updatedAt = now()
            ),
            Card(
                id = generateId(),
                cardName = "Travel Card",
                holderName = "John Doe",
                cardNumber = "378282246310005",
                expiryMonth = "09",
                expiryYear = "26",
                cvv = "4444",
                cardType = "Amex",
                notes = "American Express Platinum",
                isFavorite = true,
                createdAt = now(),
                updatedAt = now()
            )
        )
        cards.forEach { cardRepository.insertCard(it) }
    }

    suspend fun seedNotes() = withContext(Dispatchers.IO) {
        val notes = listOf(
            Note(
                id = generateId(),
                title = "Personal Goals",
                content = "1. Learn Kotlin\n2. Build Vaultix\n3. Gym 3x a week",
                color = "#BBDEFB", // Light Blue hex string
                isFavorite = true,
                createdAt = now(),
                updatedAt = now()
            ),
            Note(
                id = generateId(),
                title = "Shopping List",
                content = "- Milk\n- Eggs\n- Bread\n- Coffee",
                color = "#F8BBD0", // Pink
                isFavorite = false,
                createdAt = now(),
                updatedAt = now()
            ),
            Note(
                id = generateId(),
                title = "Wifi Password",
                content = "SSID: Home_Network\nPass: SuperSecretWifi123",
                color = "#C8E6C9", // Green
                isFavorite = true,
                createdAt = now(),
                updatedAt = now()
            ),
            Note(
                id = generateId(),
                title = "App Ideas",
                content = "A secure vault app for everything.",
                color = "#FFF9C4", // Yellow
                isFavorite = false,
                createdAt = now(),
                updatedAt = now()
            )
        )
        notes.forEach { noteRepository.insertNote(it) }
    }

    suspend fun seedIdentities() = withContext(Dispatchers.IO) {
        val identities = listOf(
            Identity(
                id = generateId(),
                documentType = "Passport",
                documentName = "US Passport",
                documentNumber = "A12345678",
                fullName = "John Doe",
                dateOfBirth = "1990-01-01",
                issuedBy = "Department of State",
                issuedDate = "2020-01-01",
                expiryDate = "2030-01-01",
                nationality = "American",
                notes = "Backup ID",
                isFavorite = true,
                createdAt = now(),
                updatedAt = now()
            ),
            Identity(
                id = generateId(),
                documentType = "Driver License",
                documentName = "UK License",
                documentNumber = "DOE900101",
                fullName = "Jane Smith",
                dateOfBirth = "1985-05-15",
                issuedBy = "DVLA",
                issuedDate = "2015-05-15",
                expiryDate = "2025-05-15",
                nationality = "British",
                notes = "Work ID",
                isFavorite = false,
                createdAt = now(),
                updatedAt = now()
            )
        )
        identities.forEach { identityRepository.insertIdentity(it) }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        passwordRepository.deleteAllPasswords()
        cardRepository.deleteAllCards()
        noteRepository.deleteAllNotes()
        identityRepository.deleteAllIdentities()
    }
}
