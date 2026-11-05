import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// The google-services plugin needs app/google-services.json. It is applied only when the file
// exists so the project still compiles before Firebase is configured. In that case the app shows
// a "Firebase not configured" screen at launch instead of crashing (see SplashActivity).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn("RideAlong: app/google-services.json not found. Firebase features are disabled until you add it (see SETUP_GUIDE.md).")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(name: String, default: String = ""): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: default).trim()

android {
    namespace = "com.example.ridealong"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.ridealong"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        // WebRTC ships native libraries for 4 CPU types; phones only need ARM (x86_64 kept for emulators).
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }

        val mapsKey = localProp("MAPS_API_KEY")
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsKey\"")
        buildConfigField("String", "ORS_API_KEY", "\"${localProp("ORS_API_KEY")}\"")
        // Optional TURN relay for in-app calls (needed when both phones are behind strict mobile NAT).
        buildConfigField("String", "TURN_URLS", "\"${localProp("TURN_URLS")}\"")
        buildConfigField("String", "TURN_USERNAME", "\"${localProp("TURN_USERNAME")}\"")
        buildConfigField("String", "TURN_PASSWORD", "\"${localProp("TURN_PASSWORD")}\"")
        buildConfigField("boolean", "USE_FIREBASE_EMULATOR", localProp("USE_FIREBASE_EMULATOR", "false").ifEmpty { "false" })
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"${localProp("FIREBASE_EMULATOR_HOST", "10.0.2.2")}\"")
        // Must start with "demo-" (emulator-only project) when no google-services.json is present.
        buildConfigField("String", "FIREBASE_EMULATOR_PROJECT_ID", "\"${localProp("FIREBASE_EMULATOR_PROJECT_ID", "demo-ridealong")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        // Java 11: Android Studio Dolphin's bundled JDK and the AGP 7.3 requirement.
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    lint {
        // Bangla (values-bn) is a partial translation; untranslated strings fall back to English.
        disable += "MissingTranslation"
        abortOnError = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Aligns kotlin-stdlib / -jdk7 / -jdk8 pulled in transitively (avoids duplicate-class errors).
    implementation(platform(libs.kotlin.bom))
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.material)

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)

    implementation(libs.glide)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)

    implementation(libs.osmdroid)
    implementation(libs.webrtc)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
