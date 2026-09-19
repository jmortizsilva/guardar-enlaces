import Foundation

/// Lo que la aplicación y su extensión de compartir tienen que saber igual.
///
/// Vive aquí y no en el objetivo de la aplicación porque una extensión es
/// otro programa: no comparte ni una línea con ella salvo lo que esté en un
/// paquete como este. Cuando esto estaba en `Guardalo/`, la extensión no
/// podía ni saber a qué servidor hablar.
public enum Configuracion {
    /// A qué servidor habla la app.
    ///
    /// En compilación de desarrollo se puede apuntar a otro sitio sin tocar
    /// código, poniendo `GUARDALO_API` en las variables de entorno del
    /// esquema de Xcode. Es el equivalente al `EXPO_PUBLIC_API_URL` del
    /// `.env` de la app de Expo.
    public static var urlApi: String {
        #if DEBUG
        ProcessInfo.processInfo.environment["GUARDALO_API"] ?? "https://api.jmortiz.es"
        #else
        "https://api.jmortiz.es"
        #endif
    }

    /// La carpeta que ven los dos programas. El nombre es el que ya usaba la
    /// app de Expo: es un grupo por cuenta de desarrollador, no por
    /// aplicación, y crear otro solo para esto no aporta nada.
    public static let grupoApp = "group.com.jmortizsilva.guardarenlaces"

    public static let nombreBaseDatos = "guardalo.db"

    /// Deliberadamente distinta de la `modoSilencioso` que usa la app de
    /// Expo: mientras las dos estén instaladas comparten grupo, y con la
    /// misma clave el interruptor de una cambiaría el comportamiento de la
    /// otra. Vuelve a ser la de siempre cuando la de Expo desaparezca.
    public static let claveGuardadoSilencioso = "guardadoSilenciosoNativa"

    /// Los ajustes que la aplicación escribe y la extensión lee.
    ///
    /// Devuelve `nil` si el grupo no está en los permisos, que en la práctica
    /// significa «compilado sin el permiso puesto». Quien llame decide qué
    /// hacer; aquí no se inventa un `UserDefaults.standard` que cada programa
    /// vería distinto.
    public static func ajustesCompartidos() -> UserDefaults? {
        UserDefaults(suiteName: grupoApp)
    }

    /// Dónde vive la base de datos que comparten los dos.
    public static func rutaBaseDatos() throws -> URL {
        try AlmacenLocal
            .carpetaCompartida(grupo: grupoApp)
            .appendingPathComponent(nombreBaseDatos)
    }
}
