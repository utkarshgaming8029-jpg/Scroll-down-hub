package com.example.data.ai

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.example.BuildConfig
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import com.google.firebase.initialize

fun configureAppCheck(context: Context, intent: Intent?) {
    Firebase.initialize(context = context)
    if (BuildConfig.DEBUG) {
        val prefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store",
            Context.MODE_PRIVATE
        )
        val token = intent?.getStringExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")
        if (!token.isNullOrEmpty()) {
            prefs.edit {
                putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", token)
            }
        }
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    } else {
        val siteKey = context.getString(R.string.recaptcha_site_key)
        if (siteKey.isNotEmpty()) {
            Firebase.appCheck.installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance(siteKey)
            )
        }
    }
}
