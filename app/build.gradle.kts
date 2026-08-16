plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.audiobible"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    buildFeatures{
        viewBinding = true
      //  buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.audiobible"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.remote.creation.compose)
    implementation(libs.androidx.runtime)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.4")

    implementation("androidx.navigation:navigation-ui-ktx:2.3.4")

    val roomVersion = "2.7.0"

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    testImplementation("com.codeborne:selenide:7.2.3")
    testImplementation("io.rest-assured:rest-assured:5.4.0")

    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    testImplementation("org.json:json:20240303")
    // Движок Ktor для пробития TLS-защиты Cloudflare в тестах
    testImplementation("io.ktor:ktor-client-core:2.3.12")
    testImplementation("io.ktor:ktor-client-apache5:2.3.12")

    val media3Version = "1.10.1" // Используйте актуальную версию

    implementation("androidx.media3:media3-session:1.10.1")
    // media compat для NotificationCompat.MediaStyle и MediaSessionCompat.Token
}