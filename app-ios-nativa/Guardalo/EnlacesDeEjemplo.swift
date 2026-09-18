import Dominio
import Foundation

/// Enlaces con fecha fija, para las vistas previas de Xcode y para las
/// pruebas de interfaz.
///
/// Las fechas son constantes a propósito: el subtítulo de cada fila lleva la
/// fecha escrita, y con `Date()` la prueba diría una cosa distinta cada día.
enum EnlacesDeEjemplo {
    /// Mediodía UTC del 15 de marzo de 2024. A mediodía no cambia de día en
    /// ninguna zona horaria de Europa ni de América.
    static let quinceDeMarzo: MarcaDeTiempo = 1_710_504_000_000

    static let todos: [Elemento] = [
        Elemento(
            id: "ejemplo-1",
            url: "https://www.xataka.com/basics/alternativas-pocket",
            titulo: "Las mejores alternativas a Pocket",
            descripcion: "Pocket cierra y estas son las opciones",
            tipo: .articulo,
            etiquetas: ["ocio", "pendiente"],
            creadoEn: quinceDeMarzo,
            actualizadoEn: quinceDeMarzo
        ),
        Elemento(
            id: "ejemplo-2",
            url: "https://www.swift.org/documentation/testing/",
            titulo: "Guía de Swift Testing",
            etiquetas: ["trabajo"],
            creadoEn: quinceDeMarzo - 86_400_000,
            actualizadoEn: quinceDeMarzo - 86_400_000
        ),
        Elemento(
            id: "ejemplo-3",
            url: "https://www.youtube.com/watch?v=abc123",
            titulo: "Cómo funciona VoiceOver en iOS",
            tipo: .video,
            etiquetas: ["pendiente"],
            creadoEn: quinceDeMarzo - 172_800_000,
            actualizadoEn: quinceDeMarzo - 172_800_000
        ),
        // Una lápida: no tiene que salir en la lista.
        Elemento(
            id: "ejemplo-borrado",
            url: "https://ejemplo.com/tirado",
            titulo: "Este se eliminó",
            creadoEn: quinceDeMarzo,
            actualizadoEn: quinceDeMarzo,
            borrado: true
        ),
    ]
}
