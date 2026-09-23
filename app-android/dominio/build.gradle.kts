// Lógica pura: sin Android, sin red y sin base de datos. Todo lo que decide
// algo vive aquí y se prueba en la JVM del Mac, sin emulador ni teléfono.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktfmt)
    // Este módulo se compila contra el JDK 21 y se ejecuta en Android 9, que no tiene todo lo
    // del JDK. Sin esto, algo como `Locale.of` compila, pasa las pruebas y revienta en un
    // teléfono con Android antiguo. Lint no lo ve: en un módulo sin Android no sabe contra
    // qué comparar (comprobado el 2026-09-23). OkHttp se protege igual.
    alias(libs.plugins.animalsniffer)
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
    signature(variantOf(libs.firmas.android.minimo) { artifactType("signature") })
    implementation(libs.serialization.json)
    testImplementation(libs.kotlin.test)
}
