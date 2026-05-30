# Vaultix - Production-Ready Secure Vault App

A fully offline, zero-knowledge Android secure vault application built with the highest security standards.

## 🔐 Security Architecture

### Encryption Stack
- **AES-256-GCM** for all field-level encryption (unique IV per operation)
- **SQLCipher** for encrypted database (AES-256-CBC at rest)
- **Android Keystore** hardware-backed key storage — keys never leave the secure element
- **PBKDF2-HMAC-SHA512** with 600,000 iterations for key derivation
- **Multi-layer**: DB encryption + field encryption (defense in depth)

### Authentication
- Master Password (PBKDF2 derived, salted)
- PIN Code (4-6 digits, PBKDF2 derived)
- Biometric (BiometricPrompt API, BIOMETRIC_STRONG)
- Panic PIN (triggers data destruction)

### Security Hardening
- `FLAG_SECURE` prevents screenshots + screen recording
- `allowBackup="false"` prevents cloud/device backups
- Root detection (su binary, build tags, Magisk)
- Emulator detection
- Debugger detection
- ProGuard with R8 full mode
- Log stripping in release builds

## 📁 Project Structure

```
app/src/main/java/com/vaultix/app/
├── VaultixApplication.kt         # Hilt + SQLCipher init
├── MainActivity.kt               # FLAG_SECURE, splash
│
├── security/
│   ├── KeystoreManager.kt        # Android Keystore (AES-256-GCM keys)
│   ├── CryptoManager.kt          # AES-256-GCM encrypt/decrypt
│   ├── KeyDerivationManager.kt   # PBKDF2-SHA512, 600k iterations
│   ├── SecurePreferences.kt      # Encrypted DataStore
│   └── SecurityChecker.kt        # Root/emulator/debugger detection
│
├── data/
│   ├── local/
│   │   ├── VaultixDatabase.kt    # Room + SQLCipher
│   │   ├── dao/                  # PasswordDao, CardDao, NoteDao, FileDao, IdentityDao
│   │   └── entity/               # All Room entities (encrypted fields)
│   ├── model/
│   │   └── VaultModels.kt        # Decrypted domain models (in-memory only)
│   └── repository/               # Encrypt on write, decrypt on read
│
├── di/
│   ├── AppModule.kt              # Security utilities DI
│   └── DatabaseModule.kt         # Encrypted DB provisioning
│
└── ui/
    ├── theme/
    │   ├── Color.kt              # Navy/Black/Orange palette
    │   ├── Type.kt               # Typography
    │   └── Theme.kt              # Material3 dark theme
    ├── navigation/
    │   ├── Screen.kt             # Route definitions
    │   └── VaultixNavGraph.kt    # Navigation with animated transitions
    ├── screens/
    │   ├── SplashScreen.kt       # Animated logo
    │   ├── OnboardingScreen.kt   # 3-page HorizontalPager
    │   ├── SetupScreen.kt        # 6-step setup flow
    │   ├── LockScreen.kt         # PIN/Password/Biometric auth
    │   ├── DashboardScreen.kt    # Stats + category grid
    │   ├── CategoryScreen.kt     # List view per category
    │   ├── ItemDetailScreen.kt   # Detail with reveal timers
    │   ├── AddEditItemScreen.kt  # Add/edit with password gen
    │   ├── SettingsScreen.kt     # All settings + panic mode
    │   ├── FileVaultScreen.kt    # Import/encrypt/view files
    │   ├── CardScanScreen.kt     # OCR card scanner
    │   ├── IdentityEditScreen.kt # Identity document editor
    │   ├── GlobalSearchScreen.kt # Search across vault
    │   ├── SecurityAuditScreen.kt # Security health overview
    │   ├── PasswordGeneratorScreen.kt
    │   ├── PdfViewerScreen.kt
    │   └── ImageViewerScreen.kt
    └── viewmodel/
        ├── AuthViewModel.kt      # Auth state, panic, auto-lock
        ├── PasswordViewModel.kt  # CRUD + password generator
        ├── CardViewModel.kt      # CRUD
        ├── NoteViewModel.kt      # CRUD + search
        ├── FileViewModel.kt      # Encrypted file vault
        ├── IdentityViewModel.kt  # Identity docs + encrypted images
        ├── GlobalSearchViewModel.kt
        ├── PasswordGeneratorViewModel.kt
        ├── SecurityAuditViewModel.kt
        ├── BackupViewModel.kt
        └── AppConfigViewModel.kt  # Appearance + app settings
```

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.x) or newer
- Android SDK 35
- JDK 17

### Setup
1. Clone/open the project in Android Studio
2. Wait for Gradle sync
3. Build & run on device (API 26+)

> **Note**: First launch triggers setup flow (create password → PIN → biometric → warning)

### Build Variants
- **debug**: Debuggable, no minification
- **release**: Full R8 minification, shrinking, no debugging

## 🔑 Key Security Decisions

| Decision | Rationale |
|----------|-----------|
| No internet permission | Zero attack surface, fully offline |
| AES-256-GCM over CBC | Authenticated encryption, tamper-proof |
| Unique IV per operation | Prevents ciphertext pattern analysis |
| 600k PBKDF2 iterations | NIST 2024 recommendation |
| Hardware-backed Keystore | Keys physically secured on device |
| SQLCipher + field encryption | Defense in depth; DB AND field level |
| No cloud backup | Prevents unauthorized data access |
| FLAG_SECURE | Blocks screenshots, screen recorders |
| Panic mode via PIN | Emergency data destruction |
| Clipboard auto-clear (30s) | Prevents clipboard sniffing |
| Reveal timer (5s) | Minimizes shoulder surfing exposure |

## 📱 Features

### ✅ Implemented
- [x] Splash screen with animated logo
- [x] 3-page onboarding
- [x] Full first-run setup flow
- [x] Lock screen with password, PIN, and biometric unlock
- [x] Dashboard with stats and category grid
- [x] Password vault with CRUD, search, generator, and strength meter
- [x] Credit card vault with masked UI and OCR-assisted scan flow
- [x] Secure notes
- [x] Identity documents with encrypted photos
- [x] Encrypted file vault with PDF/image preview
- [x] Global vault search
- [x] Security audit dashboard
- [x] Backup export/import plus local backup history
- [x] Autofill service for saved credentials
- [x] Expiry alerts for cards and identity documents
- [x] AES-256-GCM field encryption
- [x] SQLCipher encrypted database
- [x] Android Keystore integration
- [x] Panic mode for full data destruction
- [x] Auto-lock on background
- [x] Screenshot prevention with FLAG_SECURE
- [x] Root/emulator/debugger detection
- [x] Clipboard auto-clear after 30s
- [x] Password reveal timer
- [x] Offline-only app with no internet permission

### 🗺️ Plan

#### MVP
- Splash, onboarding, setup, and lock flow
- Password, card, note, identity, and file vaults
- Search, generator, security audit, autofill, and backups
- Core crypto, keystore, and anti-tamper hardening

#### In Progress
- Finish Autofill save/prompt flow on real devices
- Tighten OCR detection for card scan edge cases
- Improve backup and restore UX and error handling

#### Next Sprint
- Improve identity and card expiry workflows
- Refine file previews and search relevance

#### Backlog
- Polish settings and security audit screens
- Additional UX cleanup across vault flows

## ⚠️ Critical Notes

- **NO recovery mechanism** — by design (zero-knowledge)
- Panic mode is **irreversible** — all data is permanently destroyed
- The database passphrase is derived from the Android Keystore; if keys are deleted, data is **permanently inaccessible**
- Never store the master password anywhere — only the PBKDF2 hash is stored
