import java.util.Properties

plugins {
    // AGP 9 built-in Kotlin: do NOT apply org.jetbrains.kotlin.android.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.scalc.calculator"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.scalc.calculator"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    // Two independently buildable apps with separate application IDs (installable side by side).
    // Exchange-rate code, resources, manifest entries and test dependencies exist ONLY in the
    // "exchange" source sets (src/exchange, src/testExchange); src/main never references them.
    flavorDimensions += "edition"
    productFlavors {
        create("offline") {
            dimension = "edition"
            applicationIdSuffix = ".offline"
        }
        create("exchange") {
            dimension = "edition"
            applicationIdSuffix = ".exchange"
        }
    }

    // Release signing comes from the git-ignored keystore.properties in the project root
    // (storeFile, storePassword, keyAlias, keyPassword). Without it, release builds are unsigned.
    val keystoreFile = rootProject.file("keystore.properties")
    val releaseSigning = if (keystoreFile.isFile) {
        val props = Properties().apply { keystoreFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        release {
            signingConfig = releaseSigning
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Lists the translated languages in a generated locale config, so Android 13+ offers
        // them under Settings > System > Languages > App languages. The app has no language
        // setting of its own: it always follows the system (or that per-app system setting).
        generateLocaleConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.core)
    implementation(libs.datastore.preferences)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // JSON parsing in the Exchange flavor uses the platform org.json; JVM unit tests need a real
    // implementation instead of the android.jar stubs. Exchange tests only.
    "testExchangeImplementation"(libs.org.json)
}
