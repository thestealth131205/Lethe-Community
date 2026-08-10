plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.lethe.mediaplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.Lethe.mediaplayer"
        minSdk = 26
        targetSdk = 36
        // Version gespiegelt von der Haupt-App
        versionCode = 100547
        versionName = "10.4.147"
    }

    signingConfigs {
        create("release") {
            // GLEICHER Keystore wie die Haupt-App → identische Signatur.
            // Nur so darf der Player über die Signature-Permission auf die Lethe-Session zugreifen.
            storeFile = rootProject.file("app/lethekey.jks")
            storePassword = project.properties["KEYSTORE_PASSWORD"] as String
            keyAlias = project.properties["KEY_ALIAS"] as String
            keyPassword = project.properties["KEY_PASSWORD"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-process:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    // QR-Code (Jam beitreten/einladen) – reines Java, keine Google-Play-Services-Abhaengigkeit
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = true }

    // Network / Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Brücke suspend-Funktionen -> ListenableFuture für die Android-Auto-Browse-Callbacks (MediaLibrarySession)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")

    // Media3 – ExoPlayer + MediaSession für Hintergrund-Wiedergabe
    val media3Version = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-datasource:$media3Version")
    // SmartCache: StandaloneDatabaseProvider für den persistenten SimpleCache-Index
    implementation("androidx.media3:media3-database:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // Android Auto Verbindungserkennung (gleiche Bibliothek/Version wie die Haupt-App)
    implementation("androidx.car.app:app:1.7.0")

    // Google Cast – gleiche Receiver-ID wie die Haupt-App
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    // AppCompat-Theme wird von MediaRouteButton (Cast) benötigt
    implementation("androidx.appcompat:appcompat:1.7.0")

    testImplementation(libs.junit)
}
