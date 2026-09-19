// swift-tools-version: 6.0
import PackageDescription

/// La fontanería: SQLite, HTTP y llavero. Lo que habla con el mundo.
///
/// Depende de `Dominio`, nunca al revés: allí se decide qué hay que hacer y
/// aquí se hace. La app y la extensión de compartir usan las dos, para no
/// duplicar el cliente del servidor como hacía la app de Expo.
let package = Package(
    name: "Fontaneria",
    platforms: [.iOS(.v18), .macOS(.v14)],
    products: [
        .library(name: "Fontaneria", targets: ["Fontaneria"])
    ],
    dependencies: [.package(path: "../Dominio")],
    targets: [
        .target(
            name: "Fontaneria",
            dependencies: [.product(name: "Dominio", package: "Dominio")],
            path: "Fuentes/Fontaneria",
            linkerSettings: [.linkedLibrary("sqlite3")]
        ),
        .testTarget(
            name: "PruebasFontaneria",
            dependencies: ["Fontaneria"],
            path: "Pruebas/PruebasFontaneria"
        )
    ]
)
