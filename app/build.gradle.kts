plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingFile = providers.environmentVariable("APK_KEYSTORE_FILE")
val releaseVersionCode = providers.environmentVariable("APK_VERSION_CODE").map(String::toInt).orElse(1)

android {
    namespace = "com.plasticcheese.endoscopeviewer"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.plasticcheese.endoscopeviewer"
        minSdk = 29
        targetSdk = 36
        versionCode = releaseVersionCode.get()
        versionName = "1.0"

        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (signingFile.isPresent) {
            create("release") {
                storeFile = file(signingFile.get())
                storeType = "PKCS12"
                storePassword = providers.environmentVariable("APK_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("APK_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("APK_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.herohan:UVCAndroid:1.0.13")

    testImplementation("junit:junit:4.13.2")
}
