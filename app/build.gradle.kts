plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// El número de versión sube solo en cada compilación de GitHub (-Pvcode=N)
val vcode = (project.findProperty("vcode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.repartidor.control"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.controlrider.app"
        minSdk = 26
        targetSdk = 34
        versionCode = vcode
        versionName = "1.0.$vcode"
    }

    signingConfigs {
        create("rider") {
            storeFile = file("controlrider.keystore")
            storePassword = "controlrider"
            keyAlias = "controlrider"
            keyPassword = "controlrider"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("rider")
        }
        debug {
            signingConfig = signingConfigs.getByName("rider")
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
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.gms:play-services-ads:23.2.0")   // anuncios (AdMob)
}
