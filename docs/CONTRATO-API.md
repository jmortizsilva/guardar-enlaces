# Contrato de API — servidor-guardar-enlaces

Fuente de la verdad del API entre el backend (`servidor-guardar-enlaces`) y los
dos clientes (`Guardar enlaces/app-ios` y `Guardar enlaces/app-windows`), que
no comparten código entre sí. Cualquier cambio de este contrato se hace aquí
primero.

Base: `${URL_PUBLICA}` (variable de entorno del servidor). Todas las rutas son
relativas a esa base, sin prefijo de versión (los cambios deben ser aditivos).

Salvo las rutas bajo `/auth/`, todas exigen la cabecera:

```
Authorization: Bearer <tokenAcceso>
```

Sin ella, o con un token caducado/inválido: `401 {"error": "..."}`.

---

## Autenticación (Google / Apple, sin contraseñas)

Ni la app iOS ni la de Windows hablan directamente con Google o Apple: todo el
intercambio OAuth lo hace el servidor. Solo se entra con una cuenta cuyo
correo esté en la lista de invitados (`npm run crear-invitacion -- correo`).

### 1. `GET /auth/iniciar`

Query params:

| Nombre | Obligatorio | Valores |
|---|---|---|
| `proveedor` | sí | `google` \| `apple` |
| `modo` | sí | `deeplink` \| `polling` |
| `estado` | sí | cadena opaca generada por el cliente (aleatoria, un solo uso) |
| `esquema` | solo si `modo=deeplink` | nombre del esquema de deep link, SIN `://` (ej. `guardarenlaces`, como el campo `scheme` de `app.json` en Expo) |

Responde `302` redirigiendo al consentimiento del proveedor. El cliente debe
generar `estado` con suficiente entropía (ej. 16 bytes aleatorios en base64url).

- **iOS**: abre esta URL con `expo-web-browser` → `openAuthSessionAsync`, en
  modo `deeplink` con `esquema=guardarenlaces`. La sesión de autenticación
  nativa (`ASWebAuthenticationSession`) captura directamente la redirección
  final a `guardarenlaces://auth-callback?...`.
- **Windows**: abre esta URL con `webbrowser.open()`, en modo `polling`, y
  empieza a sondear `GET /auth/estado` (paso 4) cada 1-2 segundos.

### 2. `GET /auth/callback/:proveedor` y `POST /auth/callback/:proveedor`

Solo la usa el proveedor OAuth (registrar esta URL como *redirect URI* en
Google Cloud Console y en el *Services ID* de Sign in with Apple). **Google**
llama por `GET` con `code`/`state` en la query. **Apple** llama por `POST`
con `code`/`state` en el cuerpo `application/x-www-form-urlencoded`
(`response_mode=form_post`, obligatorio en Apple en cuanto se pide el scope
`email`). Ningún cliente debe llamar esta ruta directamente.

Resultado:
- **`modo=deeplink`**: redirige el navegador a
  `<esquema>://auth-callback?codigo=<codigoCanje>` (éxito) o
  `<esquema>://auth-callback?error=<motivo>` (fallo).
- **`modo=polling`**: responde una página HTML simple; el resultado se
  consulta con el siguiente endpoint.

`motivo` de error: `sin_invitacion` (el correo no está en la lista de
invitados) o `fallo_intercambio` (el proveedor rechazó el código, o error de
red).

### 3. `GET /auth/estado` (solo modo `polling`, para Windows)

Query: `estado=<el mismo valor usado en /auth/iniciar>`.

```json
// codigo aun no listo
{ "listo": false }
// exito
{ "listo": true, "codigoCanje": "..." }
// fallo (login rechazado)
{ "listo": true, "error": "sin_invitacion" }
```

`404` si el `estado` es desconocido o ya caducó (vida de ~5 minutos).

### 4. `POST /auth/canjear`

```json
{ "codigoCanje": "..." }
```

→ `200`:
```json
{
  "tokenAcceso": "...",
  "expiraEn": 1735689600000,
  "tokenRefresco": "...",
  "usuario": { "id": 1, "email": "persona@ejemplo.com", "proveedor": "google" }
}
```

`400` si el código es inválido, ya se usó, o caducó (un solo uso, ~60s de vida).

### 5. `POST /auth/renovar`

```json
{ "tokenRefresco": "..." }
```

→ `200` con un par nuevo (mismo formato que el canje, sin `usuario`); el
token de refresco usado queda revocado (rotación: si se reutiliza, falla).
`401` si es inválido, ya rotado o caducado — el cliente debe volver a iniciar
sesión desde cero.

### 6. `POST /auth/logout`

```json
{ "tokenRefresco": "..." }
```

→ `200 {"ok": true}`. Revoca esa sesión (no hace falta estar autenticado con
el token de acceso: perder el de refresco ya es suficiente para cerrar sesión).

### 7. `POST /auth/logout-todas` (requiere `Authorization: Bearer`)

→ `200 {"ok": true}`. Revoca todas las sesiones del usuario — usar si se
pierde un dispositivo.

### 8. `POST /auth/dev-login` — SOLO DESARROLLO, no existe salvo `PERMITIR_LOGIN_DEV=true`

```json
{ "email": "invitado@ejemplo.com" }
```

→ mismo formato de respuesta que `/auth/canjear`. Da sesión sin pasar por
Google/Apple (sigue exigiendo que el correo esté invitado). Pensado para
construir y probar los clientes antes de tener credenciales OAuth reales;
nunca debe estar activo en un servidor real.

---

## Metadatos

### `POST /metadatos`

```json
{ "url": "https://ejemplo.com/articulo" }
```

→ `200`:
```json
{ "titulo": "...", "descripcion": "...", "imagenUrl": "...", "tipo": "enlace" }
```
(`tipo`: `enlace` \| `video` \| `articulo` \| `imagen`; cualquier campo puede
ser `null` si no se encontró.)

`400` si la URL es inválida, no es http/https, o resuelve a una dirección de
red privada (no permitida). `502` si no se pudo descargar/leer la página.

Se llama justo al pegar una URL en la pantalla de "Añadir enlace", para
mostrar una vista previa antes de guardar. El resultado se guarda dentro del
elemento local del cliente: el `push` de sincronización nunca vuelve a llamar
a este endpoint.

---

## Sincronización de elementos

Modelo de un elemento:

```json
{
  "id": "uuid-generado-por-el-cliente",
  "url": "https://...",
  "titulo": "...",
  "descripcion": "...",
  "imagenUrl": "...",
  "tipo": "enlace",
  "etiquetas": ["ocio", "pendiente"],
  "creadoEn": 1735000000000,
  "actualizadoEn": 1735600000000,
  "borrado": false
}
```

`id` lo genera el CLIENTE (UUID v4): permite crear sin conexión y hace el
`push` idempotente. `borrado: true` es un tombstone (baja lógica): el
elemento sigue "existiendo" para que otros dispositivos se enteren de la baja.

### `GET /sincronizar?desde=<timestamp_ms>&limite=<n>`

Pull incremental. `desde=0` trae toda la biblioteca. `limite` por defecto 300,
máximo 1000.

```json
{ "elementos": [ /* ... */ ], "servidorEn": 1735600000123, "masDisponible": false }
```

Si `masDisponible` es `true`, repetir la llamada con `desde` = el mayor
`actualizadoEn` recibido, hasta que sea `false`. Cuando `masDisponible` es
`false`, usar `servidorEn` como `desde` en la siguiente sincronización normal.

### `POST /sincronizar`

Push por lotes (máximo 1000 elementos por llamada):

```json
{ "elementos": [ { "id": "...", "url": "...", "actualizadoEn": 1735600000000, "borrado": false } ] }
```

Cada entrada es una alta, edición o baja (misma operación: upsert). Campos
opcionales salvo `id` y `actualizadoEn` (una baja solo necesita esos dos, más
lo que ya se supiera del elemento). Conflictos: **gana el timestamp más
reciente**; si el servidor ya tiene algo igual o más nuevo, el cambio del
cliente se ignora y se devuelve la versión del servidor.

→ `200 { "elementos": [ /* version definitiva de cada entrada del lote */ ] }`.
El cliente debe sustituir su caché local por estas versiones (puede diferir
de lo que mandó, si perdió un conflicto).
