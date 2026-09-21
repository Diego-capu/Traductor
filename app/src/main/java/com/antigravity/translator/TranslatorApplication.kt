package com.antigravity.translator

import android.app.Application
import com.antigravity.translator.data.pref.AppPreferences
import com.antigravity.translator.data.repository.DeepLRepository
import com.antigravity.translator.data.repository.TranslationCache

class TranslatorApplication : Application() {

    lateinit var appPreferences: AppPreferences
        private set

    lateinit var translationCache: TranslationCache
        private set

    lateinit var deepLRepository: DeepLRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appPreferences = AppPreferences(this)
        translationCache = TranslationCache(this)
        deepLRepository = DeepLRepository(appPreferences, translationCache)
    }

    companion object {
        lateinit var instance: TranslatorApplication
            private set
    }
}
