plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vlesshub.vpn"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vlesshub.vpn"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // QR-сканер конфигов (ZXing)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Локальный AAR с ядром Xray-core, собранный через gomobile (см. BUILDING_CORE.md).
    // Подключается автоматически, если файл присутствует — так сборка не падает,
    // пока ядро ещё не собрано (например, при самом первом прогоне CI).
    if (file("libs/xray-core.aar").exists()) {
        implementation(files("libs/xray-core.aar"))
    }
}
