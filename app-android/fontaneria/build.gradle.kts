// Lo que habla con el mundo: SQLite, HTTP y la sesión. Depende de `dominio`,
// nunca al revés. Sigue siendo Kotlin sin Android: el Keystore y el conductor
// de SQLite del sistema los pone la app, y aquí se prueban con dobles y con
// el SQLite empaquetado.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktfmt)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(providers.gradleProperty("avisosComoErrores").isPresent)
    }
}

ktfmt { kotlinLangStyle() }

dependencies {
    api(project(":dominio"))
    api(libs.sqlite)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.okhttp)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.sqlite.bundled)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
}
