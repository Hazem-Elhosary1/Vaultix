# Add project specific ProGuard rules here.
-dontoptimize
-dontshrink

# Keep for Gson serialization in BackupManager
-keep class com.vaultix.app.util.BackupManager$BackupPayload { *; }

# ============= R8 Configuration =============
# Workaround for ConcurrentModificationException in R8
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepclassmembers class * {
    @androidx.room.Ignore <fields>;
}

# ============= General R8 Fixes =============
-ignorewarnings
-keepattributes Signature,Annotation,Exceptions

# ============= Google Tink & Transitive Dependencies =============
# Suppression for missing classes in Tink/ML Kit/Google API Client
-dontwarn com.google.api.**
-dontwarn com.google.cloud.**
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.protobuf.**
-dontwarn org.apache.http.**
-dontwarn org.joda.time.**
-dontwarn java.nio.file.**
-dontwarn sun.security.provider.**
-dontwarn javax.annotation.**

# Keep rules for Tink
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.crypto.tink.subtle.** { *; }

# ============= SQLCipher =============
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keep class net.sqlcipher.Cursor { *; }
-dontwarn net.sqlcipher.**

# ============= Room Database =============
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-dontwarn androidx.room.paging.**

# ============= Hilt Dependency Injection =============
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ============= WorkManager =============
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }

# ============= Biometric =============
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# ============= Gson Serialization =============
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class com.google.code.gson.** { *; }

# ============= App Specific Keep Rules =============
-keep class com.vaultix.app.data.local.entity.** { *; }
-keep class com.vaultix.app.data.model.** { *; }
-keep class com.vaultix.app.util.** { *; }
-keep class com.vaultix.app.security.** { *; }
-keep class com.vaultix.app.ui.viewmodel.** { *; }

# ============= End of Rules =============

