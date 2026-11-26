plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "co.edu.unal.nearbyplaces"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "co.edu.unal.nearbyplaces"
        minSdk = 24
        targetSdk = 37
        // versionCode = número de reto: el About lo muestra para no confundir esta app con las de retos anteriores
        versionCode = 9
        versionName = "9.0"

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
        // Robolectric lee el manifest real: corre con targetSdk y crea NearbyPlacesApp (sin esto usa un SDK viejo por defecto)
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
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)
    // Mapa de OpenStreetMap sin API key
    implementation(libs.osmdroid.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // org.json real de Android en la JVM: PhotonParser y PhotonPoiRepository se testean con Robolectric
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
