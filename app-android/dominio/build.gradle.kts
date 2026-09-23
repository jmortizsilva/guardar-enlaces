// Lógica pura: sin Android, sin red y sin base de datos. Todo lo que decide
// algo vive aquí y se prueba en la JVM del Mac, sin emulador ni teléfono.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktfmt)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Los avisos cuentan como error solo desde `verificar`, para no
        // estorbar mientras se escribe. Igual que en iOS.
        allWarningsAsErrors.set(providers.gradleProperty("avisosComoErrores").isPresent)
    }
}

ktfmt { kotlinLangStyle() }

dependencies {
    implementation(libs.serialization.json)
    testImplementation(libs.kotlin.test)
}
