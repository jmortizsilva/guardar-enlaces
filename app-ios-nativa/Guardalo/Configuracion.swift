import Foundation

enum Configuracion {
    /// A qué servidor habla la app.
    ///
    /// En compilación de desarrollo se puede apuntar a otro sitio sin tocar
    /// código, poniendo `GUARDALO_API` en las variables de entorno del
    /// esquema de Xcode. Es el equivalente al `EXPO_PUBLIC_API_URL` del
    /// `.env` de la app de Expo.
    static var urlApi: String {
        #if DEBUG
        ProcessInfo.processInfo.environment["GUARDALO_API"] ?? "https://api.jmortiz.es"
        #else
        "https://api.jmortiz.es"
        #endif
    }
}
