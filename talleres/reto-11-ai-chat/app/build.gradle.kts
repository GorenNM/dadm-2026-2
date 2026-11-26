plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "co.edu.unal.aichat"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "co.edu.unal.aichat"
        minSdk = 24
        targetSdk = 37
        // versionCode = número de reto: el About lo muestra para no confundir esta app con las de retos anteriores
        versionCode = 11
        versionName = "11.0"

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
        // Robolectric lee el manifest real: corre con targetSdk y crea ChatApp (sin esto usa un SDK viejo por defecto)
        unitTests.isIncludeAndroidResources = true
        // Robolectric con SDK 37 toca internos del JDK (FileDescriptor) y carga librerías nativas con System.load:
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
    implementation(platform(libs.firebase.bom))
    // Gemini vía Firebase AI Logic; la versión la fija el BoM
    implementation(libs.firebase.ai)
    // App Check: cada llamada al modelo lleva un token que prueba que viene de esta app.
    // Debug: token que se registra a mano en la consola; release: Play Integrity (la librería de debug no entra al release)
    debugImplementation(libs.firebase.appcheck.debug)
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Solo para el test que crea ChatApp con el manifest real
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// Lee app/google-services.json (fuera de git: ver README). Sin el archivo la app igual compila y,
// al abrirla, avisa que falta configurar Firebase en vez de caerse.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}