plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "co.edu.unal.tourismregistry"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "co.edu.unal.tourismregistry"
        minSdk = 24
        targetSdk = 37
        // versionCode = número de reto: el About lo muestra para no confundir esta app con las de retos anteriores
        versionCode = 10
        versionName = "10.0"

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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    testOptions {
        // Robolectric lee el manifest real: corre con targetSdk y crea TourismApp (sin esto usa un SDK viejo por defecto)
        unitTests.isIncludeAndroidResources = true
        // Robolectric con SDK 37 toca internos del JDK (FileDescriptor) y carga librerias nativas con System.load:
        // el JDK del IDE (25) cierra lo primero y avisa por lo segundo
        unitTests.all { test ->
            test.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--enable-native-access=ALL-UNNAMED")
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // org.json real en la JVM: en un test normal es el stub del android.jar (no hace nada); Robolectric trae el mismo org.json que el telefono
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
