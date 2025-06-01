plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.marinov.colegioetapalegacy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marinov.colegioetapalegacy"
        minSdk = 18
        targetSdk = 19
        versionCode = 1
        versionName = "1.1"
        multiDexEnabled = true
        android.buildFeatures.buildConfig = true
        buildConfigField("String", "EAD_URL", "\"${project.properties["EAD_URL"]}\"")
        buildConfigField("String", "GITHUB_PAT", "\"${project.properties["GITHUB_PAT"]}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    // Bibliotecas de UI compatíveis com API 19
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")

    // Navigation compatível
    implementation("androidx.navigation:navigation-ui:2.3.5")
    implementation("androidx.navigation:navigation-fragment:2.3.5")

    // WebView compatível
    implementation("androidx.webkit:webkit:1.4.0")
    implementation("org.mozilla.geckoview:geckoview:100.0.20220425210429")
    // Core e utilitários
    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.work:work-runtime-ktx:2.7.1") // Versão compatível com API 19

    // Networking e parsing
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
    implementation("com.google.code.gson:gson:2.8.9")

    // HTML parsing
    implementation("org.jsoup:jsoup:1.14.3")

    // Imagens
    implementation("com.github.bumptech.glide:glide:4.13.2")
    implementation("androidx.multidex:multidex:2.0.1")

    // Utilitários
    implementation("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")
    implementation("com.google.guava:guava:31.0.1-android")

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}