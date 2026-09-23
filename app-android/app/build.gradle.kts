plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.jmortizsilva.guardarenlaces"
    // 37 porque Compose 1.12 y OkHttp 5.5 ya lo exigen para compilar. El
    // objetivo sigue en 36: compilar contra la 37 no cambia cómo se comporta
    // la app en el teléfono, eso lo decide targetSdk.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jmortizsilva.guardarenlaces"
        // 28 por la accesibilidad: encabezados y títulos de panel llegan a
        // TalkBack desde aquí. Ver PLAN.md.
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Las pruebas de `src/androidTest` corren en el teléfono: el Keystore y el SQLite del
        // sistema no existen en el Mac.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Robolectric toca por dentro clases de Java que desde el JDK 17
            // están cerradas. Sin esto, cada prueba falla al arrancar con
            // «Failed to interact with raw FileDescriptor internals». La
            // lista es la de su guía de inicio.
            all {
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                )
            }
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // Avisan de que existe una versión más nueva. Las versiones se fijan
        // a propósito y se suben a mano; con esto activo, `verificar` pasaría
        // hoy y fallaría mañana sin haber tocado nada.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // targetSdk 36 es una decisión (PLAN.md): la 37 cuando Compose la pida.
        disable += "OldTargetApi"
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(providers.gradleProperty("avisosComoErrores").isPresent)
    }
}

ktfmt { kotlinLangStyle() }

dependencies {
    implementation(project(":fontaneria"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.sqlite.framework)
    implementation(libs.browser)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.junit)
}
