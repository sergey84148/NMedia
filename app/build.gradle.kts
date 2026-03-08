import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "ru.netology.nmedia"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.netology.nmedia"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["usesCleartextTraffic"] = false
        }
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = true
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(libs.glide)
    implementation(libs.cronet.embedded)
    implementation(libs.play.services.fido)
    annotationProcessor(libs.glide.compiler)

    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.lifecycle.livedata.ktx)


    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.gson)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.room)
    implementation(libs.generativeai)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)


    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)

    implementation(platform(libs.firebase))
    implementation(libs.firebase.messaging)
    implementation(libs.play.services)
    implementation(libs.okhttp)
    coreLibraryDesugaring(libs.desugaring)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}