plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // com.google.gms.google-services wird NICHT hier appliziert, sondern nur bedingt
    // für den playstore-Flavor (siehe unten). Der foss/F-Droid-Build hat keine
    // google-services.json und darf das Plugin nicht laden.
}

android {
    namespace = "com.securechat.app"
    compileSdk = 36 // Geändert von 35 auf 36

    defaultConfig {
        applicationId = "com.Lethe.app"
        minSdk = 26
        targetSdk = 36 // Geändert von 35 auf 36
        versionCode = 100540
        versionName = "10.4.140"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // Distributions-Flavors (F-Droid-Umbau Phase F1):
    //  - playstore: bisheriger Google-Play-Build (FCM, Google Maps, Play Billing, Cast, Auth) – UNVERÄNDERT.
    //  - foss:      F-Droid/Community-Build. In den Folgephasen F2–F4 werden alle proprietären
    //               Abhängigkeiten (FCM, Maps inkl. Location, Billing, Cast, Auth) durch FOSS-Alternativen
    //               ersetzt. In F1 teilt foss noch denselben Code/dieselben Deps wie playstore (Gerüst).
    flavorDimensions += "dist"
    productFlavors {
        create("playstore") {
            dimension = "dist"
            buildConfigField("boolean", "IS_FOSS", "false")
        }
        create("foss") {
            dimension = "dist"
            versionNameSuffix = "-foss"
            buildConfigField("boolean", "IS_FOSS", "true")
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

    // Karten: OpenStreetMap via osmdroid (Apache-2.0, kein API-Key, F-Droid-tauglich).
    // Ersetzt Google Maps (play-services-maps + maps-compose) in BEIDEN Flavors.
    implementation("org.osmdroid:osmdroid-android:6.1.20")
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

    // Standort (für Standort-Sharing im Chat & Dating) über das native Android
    // LocationManager-API – kein Google Play Services nötig (F-Droid-tauglich).

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
    // androidx.media (MediaSessionCompat.Token) – für PlayerNotificationManager.setMediaSessionToken.
    // Kam bisher transitiv über play-services-cast; nach dessen Entfernung explizit nötig.
    implementation("androidx.media:media:1.7.0")

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

    // Google Play Billing – In-App-Käufe (Styx-Coins aufladen). NUR playstore-Flavor:
    // proprietäres SDK, für F-Droid nicht zulässig. foss nutzt stattdessen FossBillingProvider
    // (Web-Aufladung via Chrome Custom Tabs, siehe billing/BillingProvider.kt).
    "playstoreImplementation"("com.android.billingclient:billing-ktx:6.2.0")

    // ML Kit – Gesichtserkennung für Altersverifikation. NUR playstore-Flavor: proprietäres
    // SDK, für F-Droid nicht zulässig. foss nutzt stattdessen OpenCV (Haar-Cascade,
    // siehe facedetection/OpenCvFaceDetectionProvider.kt).
    "playstoreImplementation"("com.google.mlkit:face-detection:16.1.7")

    // ML Kit – Selfie-Segmentierung für Hintergrundunschärfe im Videocall, Sticker-Freistellung
    // und Live-Stream-Greenscreen. NUR playstore-Flavor: proprietäres SDK. foss nutzt
    // stattdessen MediaPipe Tasks Vision (Apache-2.0, siehe
    // segmentation/MediaPipeSegmentationProvider.kt).
    "playstoreImplementation"("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

    // FOSS/F-Droid-Ersatz für ML Kit Face Detection: OpenCV Android (Apache-2.0, Haar-Cascade,
    // Kaskaden-XMLs als App-Assets in src/foss/assets/ gebündelt).
    "fossImplementation"("org.opencv:opencv:4.9.0")

    // FOSS/F-Droid-Ersatz für ML Kit Segmentation: MediaPipe Tasks Vision (Apache-2.0,
    // Modell selfie_segmenter.tflite als App-Asset in src/foss/assets/ gebündelt).
    "fossImplementation"("com.google.mediapipe:tasks-vision:0.10.29")

    // CameraX – Kamerasteuerung für Altersverifikation
    val cameraVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
    implementation("androidx.camera:camera-video:$cameraVersion")

    // Firebase Cloud Messaging (FCM) – Push-Notifications für Hintergrund-Empfang.
    // NUR im playstore-Flavor: der foss/F-Droid-Build nutzt den WS-Foreground-Service.
    "playstoreImplementation"(platform("com.google.firebase:firebase-bom:33.7.0"))
    "playstoreImplementation"("com.google.firebase:firebase-messaging-ktx")

    // FFmpegKit – Dual-Audio-Muxing + Video-Filter für Spark Editor (nur playstore-Flavor:
    // vorgebautes Binär-AAR, F-Droid-inkompatibel. foss-Flavor nutzt Media3FfmpegProvider).
    // Lokal eingebunden (thebytearray/ffmpeg-kit v1.0.0 – kein Maven-Repo verfügbar)
    "playstoreImplementation"(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // RootEncoder – RTMP-Push für Creator-Livestream (CameraX + RTMP)
    implementation("com.github.pedroSG94.RootEncoder:library:2.5.4")

    // Chromecast – Google-freier CASTV2-Client (ChromecastV2Client, mDNS via NsdManager).
    // Ersetzt play-services-cast-framework in BEIDEN Flavors (F-Droid-tauglich).

    // Google Drive API – Backup-Upload zu Google Drive. NUR playstore-Flavor: play-services-auth
    // (Google-Sign-In) ist proprietär (siehe backup/GoogleAuthProvider.kt). Der
    // google-api-client-android-Adapter für Android (GoogleAccountCredential) bindet ebenfalls
    // transitiv play-services-auth ein, google-api-services-drive ist der zugehörige REST-Client
    // – beide daher ebenfalls playstoreImplementation-only (siehe backup/DriveBackupProvider.kt,
    // Upload-Logik liegt im PlaystoreDriveBackupProvider, foss nutzt FossDriveBackupProvider).
    "playstoreImplementation"("com.google.android.gms:play-services-auth:21.3.0")
    "playstoreImplementation"("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    "playstoreImplementation"("com.google.apis:google-api-services-drive:v3-rev20231128-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }

    // Android Auto / Car App Library – Messaging-Oberfläche im Fahrzeug (Konversationsliste)
    implementation("androidx.car.app:app:1.7.0")

    // Stripe Android SDK – Payment Sheet für alternative Zahlungsmethode. NUR playstore-Flavor:
    // die PaymentSheet-UI bietet Google Pay an und bindet dafür transitiv play-services-wallet
    // ein (proprietär, F-Droid-inkompatibel). foss nutzt stattdessen dieselbe Stripe-Web-
    // Aufladeseite wie der Play-Billing-Fallback (siehe ui/screens/StripeTopupTab.kt in
    // beiden Flavor-Sourcesets).
    "playstoreImplementation"("com.stripe:stripe-android:21.4.1")
}

// google-services-Plugin (FCM) nur anwenden, wenn ein playstore-Task gebaut wird.
// Der foss/F-Droid-Build besitzt keine google-services.json → das Plugin würde sonst
// fehlschlagen. CI baut :app:assemblePlaystoreRelease (enthält "Playstore"),
// F-Droid baut assembleFossRelease (enthält "Foss") → sauber getrennt.
if (gradle.startParameter.taskRequests.toString().contains("Playstore", ignoreCase = true)) {
    apply(plugin = "com.google.gms.google-services")
}