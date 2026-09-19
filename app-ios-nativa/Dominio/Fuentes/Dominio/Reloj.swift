import Foundation

/// Milisegundos desde el epoch, como `Date.now()` en JavaScript.
///
/// La unidad es parte del contrato de sincronización: el backend, la app de
/// Windows y esta comparan sus `actualizadoEn` entre sí para decidir qué
/// cambio gana. Basta con que uno de los tres cuente en segundos para que la
/// comparación dé siempre el mismo ganador, y en silencio.
public typealias MarcaDeTiempo = Int64

/// El reloj se inyecta en todo lo que dependa del tiempo. Así los plazos y
/// los vencimientos se prueban sin esperar de verdad, y las pruebas no se
/// vuelven inestables según lo que tarde la máquina.
public typealias Reloj = () -> MarcaDeTiempo

public func relojDelSistema() -> MarcaDeTiempo {
    Int64(Date().timeIntervalSince1970 * 1000)
}

/// Quién genera los identificadores. Se inyecta por el mismo motivo que el
/// reloj: un uuid aleatorio no se puede comprobar en una prueba.
public typealias GeneradorId = () -> String

/// En minúsculas a propósito. Los otros dos clientes generan así sus uuid
/// (`Crypto.randomUUID()` en iOS y `str(uuid.uuid4())` en Windows) y
/// `UUID().uuidString` de Swift los da en mayúsculas: el mismo identificador
/// escrito de dos formas es justo la clase de diferencia que no se ve hasta
/// que algo deja de encontrarse.
public func generarIdUnico() -> String {
    UUID().uuidString.lowercased()
}
