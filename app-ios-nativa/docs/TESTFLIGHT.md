# Subir a TestFlight

Con todo montado, subir una versión es una orden:

```bash
./publicar              # verifica, archiva, firma, sube
./publicar --solo-ipa   # se queda en el .ipa, para mirarlo antes
```

El número de compilación lo pone solo, preguntándole a Apple cuál fue el
último. La versión visible (`MARKETING_VERSION` en el proyecto) se cambia a
mano, porque esa sí es una decisión.

Tarda unos minutos en aparecer en TestFlight: Apple procesa la compilación
antes de dejarla instalar.

## Lo que hubo que montar una vez

| Qué | Dónde | Se puede por línea de comandos |
|---|---|---|
| Ficha de la app | App Store Connect | **No.** Solo en la web |
| Certificado de distribución | Llavero de firma del Mac | Sí, pero ver abajo |
| Perfiles de App Store | Uno por objetivo, creados por API | Sí |
| Icono | `Guardalo/Assets.xcassets` | Sí, `swift herramientas/generar-icono.swift` |

## El certificado vino de EAS, y no es capricho

La cuenta tenía cuatro certificados de distribución, todos creados por EAS
para las otras apps. Servían, pero **su clave privada estaba en los
servidores de Expo**, y un certificado sin su clave privada no firma nada.

Crear uno nuevo por API habría sido más limpio a largo plazo, pero Apple
limita los certificados de distribución activos: con cuatro ya podía estar en
el tope, y llegar al tope obliga a revocar, lo que rompería la firma de las
demás apps hasta que EAS generase otra.

Así que se bajó el que ya había, con `npx eas credentials` →
`credentials.json: Upload/Download…` → `Download credentials from EAS`. Eso
deja un `.p12` y un `credentials.json` **con la contraseña en claro**; los dos
se borran en cuanto el certificado está importado:

```bash
security import dist-cert.p12 -k guardalo-firma.keychain -P '<contraseña>' \
    -T /usr/bin/codesign -T /usr/bin/security
security set-key-partition-list -S apple-tool:,apple:,codesign: \
    -s -k "$(cat ~/.appstoreconnect/llavero-firma.txt)" guardalo-firma.keychain
```

El perfil que baja al lado del `.p12` es de la app desde cuya carpeta se lanzó
el comando, así que **no sirve**: los de aquí se crean por API.

## `/apps/<id>/builds` no ve lo que `/builds` sí ve

Tras la primera subida, la compilación ya estaba `VALID` y se veía en
`/v1/builds?filter[app]=<id>`, mientras `/v1/apps/<id>/builds` seguía
devolviendo una lista vacía.

No es un detalle: `herramientas/ultima-compilacion.py` usaba la segunda, así
que habría dicho «la última es la 0» y la siguiente subida habría repetido el
número 1. Apple rechaza una compilación con un número ya usado, y el mensaje
no explica de dónde sale el problema.

## Lo que Apple pregunta y aquí ya está contestado

- **Cumplimiento de exportación.** `ITSAppUsesNonExemptEncryption = NO` en el
  proyecto, que es lo que corresponde cuando lo único que hay es HTTPS. Sin
  esa clave, App Store Connect pregunta en cada compilación y la deja parada
  hasta que alguien entra a responder.
- **El icono.** Un PNG de 1024 sin transparencia; con canal alfa, la subida se
  rechaza.

## Lo que sigue sin hacerse

Probadores externos y publicación de verdad. Las dos cosas pasan por revisión
de Apple y piden la ficha entera: capturas, descripción, política de
privacidad y categoría. Para instalarla uno mismo desde TestFlight no hace
falta nada de eso.
