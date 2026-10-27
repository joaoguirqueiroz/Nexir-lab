plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "br.rootscope.ipshadow"
 compileSdk = 35
 defaultConfig { applicationId = "br.rootscope.ipshadow"; minSdk = 26; targetSdk = 35; versionCode = 5; versionName = "0.5.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("com.wireguard.android:tunnel:1.0.20260102") }
