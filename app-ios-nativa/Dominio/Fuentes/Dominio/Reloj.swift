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
public typealias Reloj = @Sendable () -> MarcaDeTiempo

public let relojDelSistema: Reloj = { Int64(Date().timeIntervalSince1970 * 1000) }
