plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.neronguyen.chat.core.testing"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-XXLanguage:+ContextParameters")
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)

    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.core)
}
