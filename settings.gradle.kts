pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://guardianproject.info/maven") }
    }
}

rootProject.name = "SecureChat"
include(":app")  // ← WICHTIG! Diese Zeile muss da sein
include(":mediaplayer")  // Eigenständige Companion-App „Lethe Medie Player"
include(":adminapp")  // Native App für die Backend-Verwaltung (admin.letheapp.de)
 