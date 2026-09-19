import SwiftUI

/// Lo que se dice en voz alta.
///
/// Está en la fontanería, y no en el objetivo de la aplicación, porque la
/// extensión de compartir anuncia lo mismo al guardar y no comparte código
/// con ella salvo los paquetes.
public enum Anuncios {
    /// Lo que hay que oír sí o sí: un resultado o un error.
    ///
    /// Va con prioridad alta, así que no lo corta ni un cambio de foco. Esto
    /// es justo lo que la app de Expo no podía hacer: en React Native la
    /// prioridad depende de la versión, y el código tenía que renunciar a
    /// ella y conformarse con saltarse la cola.
    public static func importante(_ mensaje: String) {
        var texto = AttributedString(mensaje)
        texto.accessibilitySpeechAnnouncementPriority = .high
        AccessibilityNotification.Announcement(texto).post()
    }

    /// Para avisos que se pueden perder sin consecuencias: esperan su turno y
    /// no pisan lo que se esté leyendo.
    public static func informativo(_ mensaje: String) {
        var texto = AttributedString(mensaje)
        texto.accessibilitySpeechAnnouncementPriority = .default
        AccessibilityNotification.Announcement(texto).post()
    }
}
