# Desplegar el backend en el VPS

Cómo está montado `https://api.jmortiz.es` y cómo se actualiza. Escrito el
2026-09-10, después de migrarlo de un `podman run` suelto a compose.

**Nada de este documento contiene secretos.** Los valores reales viven solo en
el `.env` del servidor.

## Dónde está cada cosa

| Qué | Dónde |
|---|---|
| Código (clon de este repositorio) | `~/guardar-enlaces` |
| Despliegue (`docker-compose.yml` y `.env`) | `~/compose/guardar-enlaces` |
| Base de datos SQLite | `~/podman-volumes/guardar-enlaces/datos/enlaces.sqlite` |

El `.env` y la base de datos **no están en git**: un `git pull` nunca los toca.
La base de datos vive fuera del contenedor, así que recrearlo no la afecta.

**Podman rootless, nunca `docker`**: la cuenta no tiene acceso al Docker del
sistema, es a propósito. Se usa `podman` y `podman-compose`. Misma convención
que `servidor-notificaciones`, documentada en `~/DESPLIEGUE.md` del servidor.

El contenedor escucha en el 8081 por dentro y se publica en el **8092**; Caddy
hace de proxy desde `api.jmortiz.es`. El 8081 no se usa fuera porque es también
el puerto por defecto de Metro, el empaquetador de Expo.

## Actualizar

```bash
cd ~/guardar-enlaces && git pull
cd ~/compose/guardar-enlaces && podman-compose down
podman-compose up -d --build
```

El `down` hace falta: sin él, `up` choca con que el contenedor ya existe.

Comprobar que ha entrado el código nuevo, y no una imagen de caché:

```bash
podman ps                                  # "Up X seconds"
podman logs --tail 20 guardar-enlaces
```

Si el build sale entero con `Using cache` en los pasos que copian `src`, es que
**no había código nuevo que traer**: mira que el `git pull` no dijera
`Already up to date` por tener commits sin subir a GitHub.

## Variables de entorno (`~/compose/guardar-enlaces/.env`)

Nueve líneas. Ver `backend/README.md` para qué hace cada una.

```
URL_PUBLICA=https://api.jmortiz.es
ENLACES_TOKEN_SECRET=...
PERMITIR_LOGIN_DEV=false
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
APPLE_CLIENT_ID=x
APPLE_TEAM_ID=x
APPLE_KEY_ID=x
APPLE_PRIVATE_KEY=x
```

- `URL_PUBLICA` es la que se olvida y rompe el login: con ella se construye la
  dirección de vuelta que se manda a Google (`${URL_PUBLICA}/auth/callback/google`),
  y tiene que coincidir **carácter a carácter** con la registrada en Google
  Cloud Console.
- `PERMITIR_LOGIN_DEV` debe estar en `false`. A `true` abre `/auth/dev-login`,
  que da sesión con solo escribir un correo, sin contraseña.
- Las de Apple siguen en el marcador `x`: Sign in with Apple no está montado.
  El servidor lo detecta y responde `503` si alguien pide ese proveedor, en vez
  de mandarlo a Apple con un `client_id` vacío.

Un aviso que costó una tarde: `proveedorConfigurado()` solo comprueba que la
variable **no esté vacía**. Con el marcador `x` puesto, `/auth/iniciar` responde
`302` como si todo estuviera bien y el fallo solo aparece al volver de Google.
Para saber si las credenciales son de verdad, hay que mirar el `client_id` de la
redirección:

```bash
curl -s -i "https://api.jmortiz.es/auth/iniciar?proveedor=google&modo=polling&estado=prueba" | grep -i location
```

## Copia de seguridad y consultas a la base de datos

Antes de cualquier cosa que la toque, con las apps cerradas:

```bash
cp ~/podman-volumes/guardar-enlaces/datos/enlaces.sqlite ~/copia-enlaces.sqlite
```

Para consultarla no hace falta `sqlite3` en el sistema: el contenedor ya trae
`better-sqlite3`. Se le pasa un script por la entrada estándar, que además evita
que el terminal parta un comando largo en varias líneas y lo rompa:

```bash
cat > /tmp/consulta.js <<'FIN'
const bd = require('better-sqlite3');
const db = bd('/app/datos/enlaces.sqlite');
const sql = 'SELECT id, proveedor, email FROM usuarios';
for (const u of db.prepare(sql).all()) console.log(u);
FIN
podman exec -i guardar-enlaces node < /tmp/consulta.js
```

**Si mueves elementos de un usuario a otro, toca también `actualizado_en`.** La
sincronización es incremental: cada cliente pide lo que haya cambiado después de
su marca, así que unas filas que cambian de dueño conservando su fecha antigua
quedan invisibles para todo el mundo. Es un fallo real que ya se cometió una vez.

## Volver atrás

```bash
cd ~/guardar-enlaces
git log --oneline -5
git checkout <commit-anterior>
cd ~/compose/guardar-enlaces && podman-compose down && podman-compose up -d --build
```
