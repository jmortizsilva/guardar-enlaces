import Foundation

/// Consultas y reorganizaciones sobre los enlaces ya guardados: buscar,
/// filtrar, contar y renombrar etiquetas.
///
/// Separado de `Sincronizacion` a propósito, aunque en los otros dos clientes
/// esto viva en el mismo fichero: allí se fusiona con el servidor, aquí solo
/// se mira y se reordena lo que ya hay. Son dos motivos distintos para
/// cambiar el código.
public enum Biblioteca {
    /// Filtro local sobre título, URL y etiquetas, sin distinguir mayúsculas.
    public static func buscar(_ elementos: [Elemento], texto: String) -> [Elemento] {
        let consulta = texto.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !consulta.isEmpty else {
            return elementos
        }
        return elementos.filter { elemento in
            (elemento.titulo ?? "").lowercased().contains(consulta)
                || elemento.url.lowercased().contains(consulta)
                || elemento.etiquetas.contains { $0.lowercased().contains(consulta) }
        }
    }

    /// Las etiquetas que de verdad lleva algún enlace, ordenadas. Para el
    /// filtro de la lista.
    public static func etiquetasEnUso(_ elementos: [Elemento]) -> [String] {
        Set(elementos.flatMap(\.etiquetas))
            .sorted { $0.localizedCompare($1) == .orderedAscending }
    }

    /// Cuántos enlaces lleva cada etiqueta, para poder decirlo antes de
    /// renombrarla o borrarla.
    public static func recuentoPorEtiqueta(_ elementos: [Elemento]) -> [String: Int] {
        var recuento: [String: Int] = [:]
        for elemento in elementos {
            for etiqueta in elemento.etiquetas {
                recuento[etiqueta, default: 0] += 1
            }
        }
        return recuento
    }

    /// Sin etiqueta (nula o vacía) no filtra nada.
    public static func filtrarPorEtiqueta(_ elementos: [Elemento], etiqueta: String?) -> [Elemento]
    {
        guard let etiqueta, !etiqueta.isEmpty else {
            return elementos
        }
        return elementos.filter { $0.etiquetas.contains(etiqueta) }
    }

    /// Los elementos que llevaban `vieja`, con esa etiqueta cambiada por
    /// `nueva`. Devuelve solo los que cambian, que son los que hay que subir.
    ///
    /// Si un elemento ya tenía las dos, no se queda con la etiqueta repetida:
    /// dos etiquetas fundiéndose en una es un resultado válido, no un error.
    public static func renombrarEtiqueta(
        en elementos: [Elemento],
        vieja: String,
        nueva: String,
        ahora: Reloj = relojDelSistema
    ) -> [Elemento] {
        elementos
            .filter { $0.etiquetas.contains(vieja) }
            .map { elemento in
                var vistas = Set<String>()
                let renombradas = elemento.etiquetas
                    .map { $0 == vieja ? nueva : $0 }
                    .filter { vistas.insert($0).inserted }
                return elemento.conEtiquetas(renombradas, ahora: ahora)
            }
    }

    /// Los elementos que llevaban `etiqueta`, sin ella. Solo los que cambian.
    public static func quitarEtiqueta(
        en elementos: [Elemento],
        etiqueta: String,
        ahora: Reloj = relojDelSistema
    ) -> [Elemento] {
        elementos
            .filter { $0.etiquetas.contains(etiqueta) }
            .map { $0.conEtiquetas($0.etiquetas.filter { $0 != etiqueta }, ahora: ahora) }
    }
}
