package com.vaultix.app.di

import android.content.Context
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeyDerivationManager
import com.vaultix.app.security.SecurePreferences
import com.vaultix.app.security.SecurityChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager = CryptoManager()

    @Provides
    @Singleton
    fun provideKeyDerivationManager(): KeyDerivationManager = KeyDerivationManager()

    @Provides
    @Singleton
    fun provideSecurePreferences(@ApplicationContext context: Context): SecurePreferences =
        SecurePreferences(context)

    @Provides
    @Singleton
    fun provideSecurityChecker(@ApplicationContext context: Context): SecurityChecker =
        SecurityChecker(context)
}
