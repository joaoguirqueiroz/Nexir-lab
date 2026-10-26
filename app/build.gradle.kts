plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.vertice81.nexir"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.vertice81.nexir"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
