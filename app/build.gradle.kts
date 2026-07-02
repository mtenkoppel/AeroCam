plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

val releaseKeystorePath = providers.environmentVariable("AEROCAM_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("AEROCAM_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("AEROCAM_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("AEROCAM_KEY_PASSWORD").orNull

android {
    namespace = "com.karoopoc"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.karoopoc"
        minSdk = 23
        targetSdk = 34
        versionCode = 13
        versionName = "0.9.0-beta.3-dev.6"
    }

    signingConfigs {
        if (
            releaseKeystorePath != null &&
            releaseKeystorePassword != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null
        ) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    testImplementation("junit:junit:4.13.2")
}
