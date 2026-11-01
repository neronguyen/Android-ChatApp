import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.neronguyen.chat.core.network"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        minSdk = 26

        val secretsFile = rootProject.file("local.properties")
        val properties = Properties()
        if (secretsFile.exists()) {
            secretsFile.inputStream().use { properties.load(it) }
        }

        val baseUrl = properties.getProperty("BASE_URL") ?: "http://localhost/"
        buildConfigField(type = "String", name = "BASE_URL", value = "\"$baseUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-XXLanguage:+ContextParameters")
    }
}

dependencies {
    implementation(projects.core.common)

    // Arrow
    implementation(libs.arrow.core)

    // Contract
    api(libs.contract.common)
    api(libs.contract.auth)
    api(libs.contract.chat)

    // Coil
    implementation(libs.coil.kt.network.okhttp)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // KotlinX
    implementation(libs.kotlinx.serialization.json)

    // Retrofit
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlin.serialization)

    // Unit Testing
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
