// Genera el icono de la aplicación: el mismo marcador de libro blanco sobre
// azul que la app de Windows, en el PNG de 1024 que pide la App Store.
//
//    swift herramientas/generar-icono.swift
//
// Se dibuja por código y no se guarda un .png hecho a mano, por lo mismo que
// en `app-windows/recursos/generar_icono.py`: así el icono se cambia leyendo
// cuatro números, y en una revisión se ve qué cambia de verdad. Un PNG no se
// puede diffear.
//
// Dos diferencias con el de Windows, y las dos son de iOS:
//
// - Sin esquinas redondeadas ni margen. iOS recorta el icono con su propia
//   máscara; si se redondea aquí, se redondea dos veces y queda un borde azul
//   pegado al contorno.
// - Sin transparencia. La App Store rechaza un icono con canal alfa, así que
//   el mapa de bits se crea sin él.

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let lado = 1024
let azul = CGColor(red: 21 / 255, green: 87 / 255, blue: 172 / 255, alpha: 1)
let blanco = CGColor(gray: 1, alpha: 1)

let destino = URL(
    fileURLWithPath: CommandLine.arguments.count > 1
        ? CommandLine.arguments[1]
        : "Guardalo/Assets.xcassets/AppIcon.appiconset/icono.png")

guard
    let contexto = CGContext(
        data: nil,
        width: lado,
        height: lado,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
    )
else {
    fatalError("no se pudo crear el lienzo")
}

let g = CGFloat(lado)
contexto.setFillColor(azul)
contexto.fill(CGRect(x: 0, y: 0, width: g, height: g))

// El marcador: un rectángulo alto y centrado con una muesca en la base. A
// tamaño pequeño, las figuras con huecos finos (un eslabón, un clip) se
// emborronan hasta ser una mancha; una silueta maciza con una muesca grande
// se sigue reconociendo.
//
// Las proporciones son las de Windows. Lo que cambia es el eje: aquí el
// origen está abajo a la izquierda, y allí arriba, así que las alturas van
// restadas del lado.
let ancho = g * 0.40
let izquierda = (g - ancho) / 2
let derecha = izquierda + ancho
let arriba = g - g * 0.20
let abajo = g - g * 0.78
let vertice = g - (g * 0.78 - g * 0.22)

contexto.setFillColor(blanco)
contexto.beginPath()
contexto.move(to: CGPoint(x: izquierda, y: arriba))
contexto.addLine(to: CGPoint(x: derecha, y: arriba))
contexto.addLine(to: CGPoint(x: derecha, y: abajo))
contexto.addLine(to: CGPoint(x: (izquierda + derecha) / 2, y: vertice))
contexto.addLine(to: CGPoint(x: izquierda, y: abajo))
contexto.closePath()
contexto.fillPath()

guard
    let imagen = contexto.makeImage(),
    let salida = CGImageDestinationCreateWithURL(
        destino as CFURL,
        UTType.png.identifier as CFString,
        1,
        nil
    )
else {
    fatalError("no se pudo escribir \(destino.path)")
}

CGImageDestinationAddImage(salida, imagen, nil)
guard CGImageDestinationFinalize(salida) else {
    fatalError("no se pudo cerrar \(destino.path)")
}

print("icono escrito en \(destino.path) (\(lado)x\(lado))")
