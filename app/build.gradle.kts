import java.util.Properties

// --- Backend OYL: host y puertos configurables sin tocar código ---
// Prioridad: local.properties (oylDevHost, oylGatewayPort, ...) > gradle.properties > valor por defecto.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun prop(key: String, fallback: String): String =
    localProps.getProperty(key) ?: (findProperty(key) as String?) ?: fallback

// 10.0.2.2 es el "localhost" del computador visto desde el emulador de Android Studio.
// Para un teléfono físico: oylDevHost=192.168.1.X (misma red WiFi que el computador con Docker).
val devHost = prop("oylDevHost", "10.0.2.2")
val gatewayPort = prop("oylGatewayPort", "8080")
val usuariosPort = prop("oylUsuariosPort", "8081")
val productosPort = prop("oylProductosPort", "8082")
val ventasPort = prop("oylVentasPort", "8083")
val corePort = prop("oylCorePort", "8084")
// Producción: base HTTPS propia, sin tocar código.
val prodBaseUrl = prop("oylProdBaseUrl", "https://api.oylfrutossecos.cl/").trimEnd('/') + "/"

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 trae soporte de Kotlin integrado: no se aplica org.jetbrains.kotlin.android.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // com.google.gms.google-services se agregará en la Fase 7 (FCM), cuando exista
    // un google-services.json real fuera del repositorio. Ver README.
}

android {
    namespace = "cl.oyl.frutossecos"
    compileSdk = 37

    defaultConfig {
        applicationId = "cl.oyl.frutossecos"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-fase1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // true = backend real vía Retrofit con caché Room (offline-first).
        // false = modo 100 % local (comportamiento original de la app).
        val useRemote = prop("oylUseRemote", "true").toBoolean()
        buildConfigField("boolean", "USE_REMOTE_REPOSITORIES", useRemote.toString())

        // Host y puertos del backend, en un solo lugar.
        buildConfigField("String", "API_HOST", "\"$devHost\"")
        buildConfigField("int", "API_GATEWAY_PORT", gatewayPort)
        buildConfigField("int", "API_USUARIOS_PORT", usuariosPort)
        buildConfigField("int", "API_PRODUCTOS_PORT", productosPort)
        buildConfigField("int", "API_VENTAS_PORT", ventasPort)
        buildConfigField("int", "API_CORE_PORT", corePort)
        // Endpoint principal (ms-core a través del gateway).
        buildConfigField("String", "API_BASE_URL", "\"http://$devHost:$gatewayPort/api/v1/\"")
    }

    // Firma de release: keystore.properties (fuera de git) o variables de entorno OYL_*.
    // Nunca se versiona el keystore ni sus contraseñas (ver README).
    val signingProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signingValue(key: String, env: String): String? = signingProps.getProperty(key) ?: System.getenv(env)
    val releaseStoreFile = signingValue("storeFile", "OYL_KEYSTORE_FILE")

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "OYL_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "OYL_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "OYL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Desarrollo: emulador (10.0.2.2) o IP LAN configurada en local.properties.
            versionNameSuffix = "-dev"
        }
        release {
            // Producción: URL HTTPS propia (oylProdBaseUrl) y sin tráfico en claro.
            buildConfigField("String", "API_BASE_URL", "\"${prodBaseUrl}api/v1/\"")
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                // Capturas de contraste (ContrastScreenshotTest): -PoylScreenshotDir=/ruta para elegir carpeta.
                it.systemProperty(
                    "oyl.screenshotDir",
                    (project.findProperty("oylScreenshotDir") as String?)
                        ?: layout.buildDirectory.dir("screenshots").get().asFile.path,
                )
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.osmdroid.android)
    implementation(libs.play.services.location)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.lottie.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
