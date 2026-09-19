import Dominio
import Foundation

/// Guardar un enlace, que es lo único que hacen las dos: la pantalla de
/// añadir y la extensión de compartir.
///
/// Está aquí y no en cada una porque el comportamiento con un enlace repetido
/// (actualizar el que había, sumar las etiquetas, conservar el título si la
/// comprobación no trajo ninguno) es justo lo que costó acordar entre los
/// clientes, y con dos copias volvería a separarse sin que nadie lo notara
/// hasta usarlas por separado.
public enum GuardarEnlace {
    public enum Resultado: Sendable {
        case nuevo(Elemento)
        case actualizado(Elemento)

        public var elemento: Elemento {
            switch self {
            case .nuevo(let elemento), .actualizado(let elemento): elemento
            }
        }

        /// Lo que se dice en voz alta. Sale de aquí y no de cada pantalla
        /// para que compartir desde Safari y guardar desde la aplicación
        /// suenen igual.
        public func anuncio(seComprobo: Bool) -> String {
            switch self {
            case .nuevo(let elemento):
                seComprobo
                    ? Textos.guardado(titulo: Presentacion.titulo(de: elemento))
                    : Textos.guardadoSinComprobar
            case .actualizado(let elemento):
                Textos.actualizado(titulo: Presentacion.titulo(de: elemento))
            }
        }
    }

    /// Deja el enlace guardado y encolado para subir.
    ///
    /// `metadatos` puede venir vacío y no pasa nada: se guarda igual, sin
    /// título. Comprobar la página nunca fue lo importante.
    @discardableResult
    public static func guardar(
        url: String,
        etiquetas: [String],
        metadatos: MetadatosExtraidos?,
        en almacen: AlmacenLocal,
        ahora: @escaping Reloj = relojDelSistema
    ) throws -> Resultado {
        let guardados = Sincronizacion.elementosVisibles(try almacen.cargarTodos())
        let resultado: Resultado
        if let repetido = Duplicados.buscar(en: guardados, url: url) {
            resultado = .actualizado(
                repetido.actualizado(con: metadatos, etiquetasNuevas: etiquetas, ahora: ahora)
            )
        } else {
            resultado = .nuevo(
                nuevoElementoLocal(
                    DatosElementoNuevo(
                        url: url,
                        titulo: metadatos?.titulo,
                        descripcion: metadatos?.descripcion,
                        imagenUrl: metadatos?.imagenUrl,
                        tipo: metadatos?.tipo ?? .enlace,
                        etiquetas: etiquetas
                    ),
                    ahora: ahora
                )
            )
        }
        try almacen.marcarPendiente(resultado.elemento)
        return resultado
    }
}
