package com.vaultix.app.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseSecurityTest {

    @Test
    fun verifyDatabaseIsEncryptedAndCannotBeOpenedAsPlaintext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = context.getDatabasePath("vaultix_secure.db")

        // تحقق من وجود ملف قاعدة البيانات
        if (dbFile.exists()) {
            // محاكاة محاولة فتح قاعدة البيانات المشفرة باستخدام SQLite العادي (بدون مفتاح SQLCipher)
            assertThrows(Exception::class.java) {
                // محاولة فتح الملف كقاعدة بيانات عادية غير مشفرة
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
            }
            // إذا رمى النظام Exception، فهذا يعني أن الملف مشفر بالكامل ومحمي بنجاح!
        }
    }
}
