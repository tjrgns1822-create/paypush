plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.paylog.collector"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.paylog.collector"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

// 외부 라이브러리 없이 안드로이드 기본 기능만 쓴다. 테스트용 JUnit만 추가.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
