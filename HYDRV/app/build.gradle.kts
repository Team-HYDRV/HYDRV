import org.gradle.api.GradleException
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        FileInputStream(propsFile).use { load(it) }
    }
}

fun releaseSigningValue(propertyName: String, envName: String): String? {
    return keystoreProperties.getProperty(propertyName)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }
}

android {
    namespace = "app.hydra.manager"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "app.hydra.manager"
        minSdk = 23
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseStoreFile = releaseSigningValue("storeFile", "HYDRV_RELEASE_STORE_FILE")
                ?: throw GradleException("Missing release signing configuration: storeFile")
            val releaseStorePassword = releaseSigningValue("storePassword", "HYDRV_RELEASE_STORE_PASSWORD")
                ?: throw GradleException("Missing release signing configuration: storePassword")
            val releaseKeyAlias = releaseSigningValue("keyAlias", "HYDRV_RELEASE_KEY_ALIAS")
                ?: throw GradleException("Missing release signing configuration: keyAlias")
            val releaseKeyPassword = releaseSigningValue("keyPassword", "HYDRV_RELEASE_KEY_PASSWORD")
                ?: throw GradleException("Missing release signing configuration: keyPassword")

            signingConfig = signingConfigs.create("release").apply {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
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
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.picasso:picasso:2.8")
    implementation("com.google.android.gms:play-services-ads:25.1.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
