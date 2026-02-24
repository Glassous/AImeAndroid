plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

import java.util.Properties
import java.io.FileInputStream

val envProps = Properties()
val envLocalFile = rootProject.file(".env.local")
if (envLocalFile.exists()) {
    envProps.load(FileInputStream(envLocalFile))
} else {
    val envExampleFile = rootProject.file(".env.example")
    if (envExampleFile.exists()) {
        envProps.load(FileInputStream(envExampleFile))
    }
}

android {
    namespace = "com.glassous.aime"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.glassous.aime"
        minSdk = 33
        targetSdk = 36
        versionCode = 8
        versionName = "2.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        val defaultModelsUrl = envProps.getProperty("DEFAULT_MODELS_URL", "")
        buildConfigField("String", "DEFAULT_MODELS_URL", "\"$defaultModelsUrl\"")

        val supabaseUrl = envProps.getProperty("SUPABASE_URL", "")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")

        val supabaseKey = envProps.getProperty("SUPABASE_KEY", "")
        buildConfigField("String", "SUPABASE_KEY", "\"$supabaseKey\"")

        val shareBaseUrl = envProps.getProperty("SHARE_BASE_URL", "")
        buildConfigField("String", "SHARE_BASE_URL", "\"$shareBaseUrl\"")

        val aliyunFcProxyUrl = envProps.getProperty("ALIYUN_FC_PROXY_URL", "")
        buildConfigField("String", "ALIYUN_FC_PROXY_URL", "\"$aliyunFcProxyUrl\"")

        val yunzhiApiToken = envProps.getProperty("YUNZHI_API_TOKEN", "")
        buildConfigField("String", "YUNZHI_API_TOKEN", "\"$yunzhiApiToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Material3 Window Size Class
    implementation(libs.androidx.material3.window.size)

    // Material3 Expressive (for LoadingIndicator and related components)
    implementation("androidx.compose.material3:material3-android:1.5.0-alpha07")
    
    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    
    // HTML parsing for web search
    implementation("org.jsoup:jsoup:1.17.2")
    
    // Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // DataStore
    implementation(libs.androidx.datastore.preferences)
    
    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Supabase
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)

    // SplashScreen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // AndroidX WebKit
    implementation("androidx.webkit:webkit:1.14.0")

    

    // Markdown rendering
    // Markwon dependencies
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-latex:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")

    implementation("com.knuddels:jtokkit:1.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
