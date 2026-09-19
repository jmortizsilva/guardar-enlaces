// swift-tools-version: 6.0
import PackageDescription

/// Logica pura de Guardalo: sin UIKit, sin SwiftUI, sin red y sin base de
/// datos. Todo lo que decide algo vive aqui y se prueba con `swift test` en
/// el propio Mac, en segundos y sin simulador.
///
/// La fontaneria (SQLite, URLSession, Keychain) va en el proyecto Xcode de al
/// lado, que consume este paquete.
let package = Package(
    name: "Dominio",
    // macOS ademas de iOS a proposito: es lo que permite que `swift test`
    // corra nativo en el Mac en vez de arrancar un simulador.
    platforms: [.iOS(.v18), .macOS(.v14)],
    products: [
        .library(name: "Dominio", targets: ["Dominio"])
    ],
    targets: [
        .target(name: "Dominio", path: "Fuentes/Dominio"),
        .testTarget(
            name: "PruebasDominio",
            dependencies: ["Dominio"],
            path: "Pruebas/PruebasDominio"
        )
    ]
)
