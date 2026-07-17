plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.securechat.app"
    compileSdk = 36 // Geändert von 35 auf 36

    defaultConfig {
        applicationId = "com.Lethe.app"
        minSdk = 26
        targetSdk = 36 // Geändert von 35 auf 36
        versionCode = 100534
        versionName = "10.4.134"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    secrets {
        // Optionally specify a different file name containing your secrets.
        // The plugin defaults to "local.properties"
        propertiesFileName = "secrets.properties"

        // A properties file containing default secret values. This file can be
        // checked in version control.
        defaultPropertiesFileName = "local.defaults.properties"

        // Configure which keys should be ignored by the plugin by providing regular expressions.
        // "sdk.dir" is ignored by default.
        ignoreList.add("keyToIgnore") // Ignore the key "keyToIgnore"
        ignoreList.add("sdk.*")       // Ignore all keys matching the regexp "sdk.*"
    }
    signingConfigs {
        create("release") {
            storeFile = file(project.properties["KEYSTORE_PATH"] as String)
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
    // Standard Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-process:2.9.2")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Room Database
    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Maps SDK for Android
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    // Maps Compose (kompatibel mit Maps SDK 18.x)
    implementation("com.google.maps.android:maps-compose:4.3.3")
    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")

    // Extended Icons
    implementation("androidx.compose.material:material-icons-extended")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")

    // Network / Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Verschlüsselte SharedPreferences für sichere Token-Speicherung (stabile Version!)
    implementation("androidx.security:security-crypto:1.0.0")

    // Coroutines (falls noch nicht da)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("io.coil-kt:coil-compose:2.5.0")
    //biometric
    implementation(libs.androidx.biometric)

    // WorkManager (Hintergrundaufgaben, z.B. Kontaktanfragen-Polling)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Standort (für Standort-Sharing im Chat & Dating)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Media3 Transformer — lokale Video-Transkodierung (Hardware-beschleunigt)
    val media3Version = "1.4.1"
    implementation("androidx.media3:media3-transformer:$media3Version")
    implementation("androidx.media3:media3-effect:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    // Media3 ExoPlayer — Video-Wiedergabe (besser als VideoView, HTTPS-kompatibel)
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-datasource:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")

    // QR-Code-Generierung (pure Java, kein JNI)
    implementation("com.google.zxing:core:3.5.3")
    // QR-Code-Scanner (ZXing Android Embedded)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = true }

    // Timber – strukturiertes Logging (kein Output in Release)
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Chrome Custom Tabs – nahtlose In-App-Webseiten (z. B. Datenschutzerklärung)
    implementation("androidx.browser:browser:1.8.0")

    // Eingebetteter Tor-Client (Guardian Project) – kein Orbot erforderlich
    implementation("info.guardianproject:tor-android:0.4.7.14")
    // jtorctl: Transitive Dependency von tor-android (net.freehaven.tor.control.*).
    // Muss explizit angegeben werden, da R8 die Klassen sonst als ungenutzt streicht.
    implementation("info.guardianproject:jtorctl:0.4")


    // WebRTC – 1:1 Video Calls (stream-webrtc-android = selbes org.webrtc-Package, auf Maven Central verfügbar)
    implementation("io.getstream:stream-webrtc-android:1.3.8")

    // Google Play Billing – In-App-Käufe (Styx-Coins aufladen)
    implementation("com.android.billingclient:billing-ktx:6.2.0")

    // ML Kit – Gesichtserkennung für Altersverifikation
    implementation("com.google.mlkit:face-detection:16.1.7")

    // ML Kit – Selfie-Segmentierung für Hintergrundunschärfe im Videocall
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

    // CameraX – Kamerasteuerung für Altersverifikation
    val cameraVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
    implementation("androidx.camera:camera-video:$cameraVersion")

    // Firebase Cloud Messaging (FCM) – Push-Notifications für Hintergrund-Empfang
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // FFmpegKit – Dual-Audio-Muxing + Video-Filter für Spark Editor
    // Lokal eingebunden (thebytearray/ffmpeg-kit v1.0.0 – kein Maven-Repo verfügbar)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // RootEncoder – RTMP-Push für Creator-Livestream (CameraX + RTMP)
    implementation("com.github.pedroSG94.RootEncoder:library:2.5.4")

    // Google Cast – Chromecast-Integration für Musik-Streaming
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")

    // Google Drive API – Backup-Upload zu Google Drive
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20231128-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }

    // Android Auto / Car App Library – Messaging-Oberfläche im Fahrzeug (Konversationsliste)
    implementation("androidx.car.app:app:1.7.0")

    // Stripe Android SDK – Payment Sheet für alternative Zahlungsmethode
    implementation("com.stripe:stripe-android:21.4.1")
}