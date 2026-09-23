// Lo que habla con el mundo: SQLite, HTTP y la sesión. Depende de `dominio`,
// nunca al revés. Sigue siendo Kotlin sin Android: el Keystore y el conductor
// de SQLite del sistema los pone la app, y aquí se prueban con dobles y con
// el SQLite empaquetado.
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
        allWarningsAsErrors.set(providers.gradleProperty("avisosComoErrores").isPresent)
    }
}

ktfmt { kotlinLangStyle() }

dependencies {
    signature(variantOf(libs.firmas.android.minimo) { artifactType("signature") })
    api(project(":dominio"))
    api(libs.sqlite)
    implementation(libs.serialization.json)
    api(libs.coroutines.core)
    implementation(libs.okhttp)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.sqlite.bundled)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
}
