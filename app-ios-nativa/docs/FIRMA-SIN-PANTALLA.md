# Firmar e instalar sin abrir Xcode

Este proyecto se trabaja por SSH: hay un Mac con Xcode, pero nadie está
delante de su pantalla. Todo lo de firmar va por línea de comandos, y hay
cuatro sitios donde eso se atasca. Están aquí para no volver a descubrirlos.

## Lo que hace falta

| Dato | Dónde está |
|---|---|
| Clave de API (`.p8`) | `~/.appstoreconnect/private_keys/AuthKey_<KeyID>.p8`, nunca en el repositorio |
| Key ID | En el nombre del fichero de la clave |
| Issuer ID | App Store Connect → Usuarios y acceso → Integraciones |
| Team ID | `S92QZXCW54`, ya puesto en el proyecto |

La clave se crea una vez en App Store Connect y solo se puede descargar en ese
momento. Si se pierde, se revoca y se crea otra.

## 1. El llavero de login está bloqueado por SSH

Sin sesión gráfica no hay quien teclee su contraseña, y `xcodebuild` falla con
**«User interaction is not allowed»**. La salida es un llavero aparte solo
para firmar, con una contraseña aleatoria guardada en un fichero:

```bash
CLAVE=$(cat ~/.appstoreconnect/llavero-firma.txt)
security unlock-keychain -p "$CLAVE" guardalo-firma.keychain
security list-keychains -d user -s guardalo-firma.keychain login.keychain-db
```

Se vuelve a bloquear solo; hay que desbloquearlo antes de cada compilación
firmada.

## 2. Ya había un certificado, y su clave privada no está aquí

Xcode propone **revocar** el certificado existente y crear otro. No se hace:
ese certificado es el que usan las demás firmas de esta cuenta, y revocarlo
las rompe todas.

En su lugar se crea uno nuevo por la API, que no toca el anterior: petición
con `openssl req`, `POST /v1/certificates` con `certificateType: DEVELOPMENT`,
y el certificado que responde se importa junto a su clave privada en el
llavero de firma. Después:

```bash
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$CLAVE" guardalo-firma.keychain
```

Sin esa última línea, `codesign` pide permiso por cada firma y por SSH no hay
quien lo dé.

## 3. Falta el intermedio de Apple, y no es el que trae el sistema

Con el certificado importado, `security find-identity` seguía diciendo **«0
valid identities found»**: falta la cadena. El certificado lo emite la
autoridad **G3**, y la que traen los llaveros del sistema es la antigua.

```bash
curl -sSL -A "Mozilla/5.0" -o g3.cer https://www.apple.com/certificateauthority/AppleWWDRCAG3.cer
security import g3.cer -k guardalo-firma.keychain
```

**El `-L` y el `-A` no son adorno**: sin ellos, Apple devuelve una página HTML
y `security` se queja de un formato que no entiende, que no tiene nada que ver
con lo que pasa de verdad.

## 4. Compilar firmado

```bash
xcodebuild -project Guardalo.xcodeproj -scheme Guardalo \
    -destination 'generic/platform=iOS' \
    -derivedDataPath .build-dispositivo \
    -allowProvisioningUpdates \
    -authenticationKeyPath ~/.appstoreconnect/private_keys/AuthKey_2V5QGQLFXK.p8 \
    -authenticationKeyID 2V5QGQLFXK \
    -authenticationKeyIssuerID f313d445-9ae7-4b5d-a10f-65aa272f36f1 \
    build
```

`-allowProvisioningUpdates` se encarga del identificador de la app y del
perfil; los tres parámetros de autenticación son los que le dan permiso para
hacerlo sin que nadie inicie sesión.

## 5. `-allowProvisioningUpdates` dice «Authentication failed» con una clave buena

Al añadir el App Group, `xcodebuild` empezó a contestar:

```
error: Authentication failed: Make sure a bearer token was provided, it is
properly configured and signed, and it has not expired.
```

La clave no tiene nada malo: el mismo `.p8` y el mismo Issuer ID firman un JWT
que la API de App Store Connect acepta con un 200 (`GET /v1/bundleIds`). Lo
que falla es la autenticación **de Xcode**, y como no puede entrar, se queda
con el perfil que ya tenía en la caché y los errores de después («doesn't
include the App Groups capability») son consecuencia de eso, no la causa.

Sin Xcode de por medio se hace todo por la API, que además es más claro
porque cada paso se ve:

| Qué | Cómo |
|---|---|
| Habilitar una capacidad | `POST /v1/bundleIdCapabilities` con `capabilityType` |
| Crear el identificador de la extensión | `POST /v1/bundleIds` |
| Crear el perfil | `POST /v1/profiles`, con el certificado y los dispositivos |
| Instalarlo | escribir el `profileContent` (base64) en `~/Library/MobileDevice/Provisioning Profiles/<uuid>.mobileprovision` |

El JWT se firma con `openssl dgst -sha256 -sign`, que devuelve DER; ES256
quiere los dos enteros crudos de 32 bytes, así que hay que convertirlo. No
hace falta instalar nada.

## 6. Asociar un App Group hay que hacerlo en la web

Esto es el único paso de todo el camino que no tiene línea de comandos.
`POST /v1/bundleIdCapabilities` habilita **la capacidad** App Groups, pero
decir *qué grupo* es otra cosa, y la API de App Store Connect no tiene
`appGroups` (404 en todas sus formas). El perfil sale con el permiso presente
y el array vacío:

```xml
<key>com.apple.security.application-groups</key>
<array/>
```

Con eso, firmar falla. Se arregla en
`developer.apple.com/account/resources/identifiers/list`: el identificador →
App Groups → Configure → marcar el grupo → Save. Después hay que **volver a
crear el perfil**: el contenido se genera al crearlo y el que ya estaba no se
entera.

El grupo del llavero no da este problema: el perfil trae `S92QZXCW54.*`, que
cubre cualquiera del equipo.

## 7. Instalar en el teléfono

El primer emparejamiento es **por cable**: el teléfono desbloqueado y
aceptando «¿Confiar en este ordenador?». Después se puede instalar por red,
con los dos en la misma wifi.

```bash
xcrun devicectl list devices                                   # ver el identificador
xcrun devicectl device install app --device <id> .build-dispositivo/.../Guardalo.app
```

## Lo que es temporal

Mientras convivan las dos apps en el mismo teléfono:

- Identificador `com.jmortizsilva.guardarenlaces.nativa`
- Esquema de enlace `guardalonativo`
- Nombre visible «Guárdalo nativo»

Las tres cosas vuelven a las de siempre cuando esta sustituya a la de Expo.
