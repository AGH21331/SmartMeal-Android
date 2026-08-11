plugins {
    id("com.android.application")
}

android {
    namespace = "com.agh21331.smartmeal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.agh21331.smartmeal"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.17.0")
}
