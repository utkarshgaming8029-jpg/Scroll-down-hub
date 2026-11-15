package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

data class AppStrings(
    val languageCode: String,
    val languageLabel: String,
    val navHome: String,
    val navSearch: String,
    val navReels: String,
    val navCreate: String,
    val navProfile: String,
    val settingsTitle: String,
    val editProfile: String,
    val posts: String,
    val followers: String,
    val following: String,
    val yourActivity: String,
    val savedPosts: String,
    val archive: String,
    val qrCode: String,
    val help: String,
    val about: String,
    val logout: String
)

private val EnglishStrings = AppStrings(
    languageCode = "en",
    languageLabel = "English",
    navHome = "Home",
    navSearch = "Search",
    navReels = "Reels",
    navCreate = "Create",
    navProfile = "Profile",
    settingsTitle = "Settings & Privacy",
    editProfile = "Edit Profile",
    posts = "Posts",
    followers = "Followers",
    following = "Following",
    yourActivity = "Your Activity",
    savedPosts = "Saved Posts",
    archive = "Archive",
    qrCode = "QR Code",
    help = "Help",
    about = "About",
    logout = "Log Out"
)

private val HindiStrings = AppStrings(
    languageCode = "hi",
    languageLabel = "हिन्दी (Hindi)",
    navHome = "होम",
    navSearch = "खोजें",
    navReels = "रील्स",
    navCreate = "बनाएं",
    navProfile = "प्रोफ़ाइल",
    settingsTitle = "सेटिंग्स और गोपनीयता",
    editProfile = "प्रोफ़ाइल संपादित करें",
    posts = "पोस्ट",
    followers = "फ़ॉलोअर्स",
    following = "फ़ॉलोइंग",
    yourActivity = "आपकी गतिविधि",
    savedPosts = "सहेजे गए पोस्ट",
    archive = "आर्काइव",
    qrCode = "QR कोड",
    help = "सहायता",
    about = "ऐप के बारे में",
    logout = "लॉग आउट"
)

private val SpanishStrings = AppStrings(
    languageCode = "es",
    languageLabel = "Español",
    navHome = "Inicio",
    navSearch = "Buscar",
    navReels = "Reels",
    navCreate = "Crear",
    navProfile = "Perfil",
    settingsTitle = "Configuración y Privacidad",
    editProfile = "Editar Perfil",
    posts = "Publicaciones",
    followers = "Seguidores",
    following = "Siguiendo",
    yourActivity = "Tu Actividad",
    savedPosts = "Guardados",
    archive = "Archivo",
    qrCode = "Código QR",
    help = "Ayuda",
    about = "Acerca de",
    logout = "Cerrar Sesión"
)

val LocalAppStrings = compositionLocalOf { EnglishStrings }

fun stringsForLanguage(code: String): AppStrings = when (code) {
    "hi" -> HindiStrings
    "es" -> SpanishStrings
    else -> EnglishStrings
}

@Composable
fun ProvideAppStrings(languageCode: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppStrings provides stringsForLanguage(languageCode)) {
        content()
    }
}
