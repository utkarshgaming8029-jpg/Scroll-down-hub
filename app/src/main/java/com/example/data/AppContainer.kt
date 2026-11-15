package com.example.data

import android.content.Context
import com.example.R
import com.example.data.ai.FirebaseAiRepository
import com.example.data.local.SessionDataStore
import com.example.data.media.VoiceAudioHelper
import com.example.data.repository.FirestoreAccountRepository
import com.example.data.repository.FirestoreEngagementRepository
import com.example.data.repository.FirestoreLiveAndVerificationRepository
import com.example.data.repository.FirestoreSocialRepository
import com.example.data.repository.MediaStorageHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
    }
    val firebaseAuth: FirebaseAuth by lazy { Firebase.auth }
    val sessionDataStore: SessionDataStore by lazy { SessionDataStore(appContext) }
    val mediaStorageHelper: MediaStorageHelper by lazy { MediaStorageHelper(appContext) }
    val aiRepository: FirebaseAiRepository by lazy { FirebaseAiRepository() }
    val voiceAudioHelper: VoiceAudioHelper by lazy { VoiceAudioHelper(appContext) }

    val accountRepository: FirestoreAccountRepository by lazy {
        FirestoreAccountRepository(firestore, firebaseAuth, sessionDataStore, mediaStorageHelper)
    }

    val socialRepository: FirestoreSocialRepository by lazy {
        FirestoreSocialRepository(firestore, firebaseAuth, mediaStorageHelper)
    }

    val engagementRepository: FirestoreEngagementRepository by lazy {
        FirestoreEngagementRepository(firestore, firebaseAuth, socialRepository, mediaStorageHelper)
    }

    val liveAndVerificationRepository: FirestoreLiveAndVerificationRepository by lazy {
        FirestoreLiveAndVerificationRepository(firestore, firebaseAuth, socialRepository)
    }
}
