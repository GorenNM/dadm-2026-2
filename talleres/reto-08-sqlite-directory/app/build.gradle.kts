plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "co.edu.unal.softwaredirectory"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "co.edu.unal.softwaredirectory"
        minSdk = 24
        targetSdk = 37
        // versionCode = número de reto: el About lo muestra para no confundir esta app con las de retos anteriores
        versionCode = 8
        versionName = "8.0"

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
        // Robolectric lee el manifest real: corre con targetSdk y crea DirectoryApp (sin esto usa un SDK viejo por defecto)
        unitTests.isIncludeAndroidResources = true
        // Robolectric con SDK 37 toca internos del JDK (FileDescriptor) y carga el SQLite nativo con System.load:
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
    // SQLite real en la JVM: los tests de DirectoryDbHelper/SqliteCompanyRepository corren con gradlew test
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
