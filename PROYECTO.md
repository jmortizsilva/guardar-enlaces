# Guárdalo — descripción del proyecto

App para guardar enlaces (artículos, vídeos) accesible con lector de
pantalla, sincronizada entre iPhone y Windows. Este documento es el punto de
entrada para quien se incorpora al proyecto: qué hay, cómo se organiza y por
dónde empezar en cada parte.

## Qué hace

Guardar una URL desde el móvil o el PC, con metadatos (título, descripción,
imagen) sacados automáticamente, organizarla por etiquetas/categorías, y
tenerla disponible en cualquiera de los dos dispositivos. Pensado desde el
principio para funcionar bien con VoiceOver (iOS) y NVDA/JAWS/Narrador
(Windows) — no es un añadido posterior.

**La cuenta es opcional en el iPhone.** La app se abre sin pedir nada y
funciona entera en local: los enlaces se guardan en el propio teléfono y los
metadatos los resuelve él mismo. La cuenta (Google o Apple, sin contraseñas)
solo hace falta para sincronizar con el PC; al entrar por primera vez se
pregunta si se quieren subir a la cuenta los enlaces que ya hubiera en el
teléfono. En Windows es igual desde septiembre de 2026: la app se abre sin
pedir nada y guarda en el propio equipo, y la cuenta solo hace falta para
sincronizar. Antes allí era obligatoria, y no entrar cerraba la aplicación.

## Estructura

Un solo repositorio por comodidad (todo centralizado en GitHub), pero
**tres proyectos independientes por dentro**: no comparten código, cada uno
con su propio gestor de paquetes, sus propias dependencias y su propio ciclo
de vida. Cuando cambies algo en uno, no asumas que el otro se entera.

| Carpeta | Qué es | Stack |
|---|---|---|
| [`backend/`](backend/README.md) | Servidor: auth (Google/Apple, sin contraseñas), metadatos de URLs, sincronización | Node + Fastify + SQLite |
| [`app-ios-nativa/`](app-ios-nativa/README.md) | Cliente iOS | Swift 6 + SwiftUI (mínimo iOS 18) |
| [`app-windows/`](app-windows/README.md) | Cliente de escritorio | Python + wxPython |

El **contrato de API** entre el backend y los dos clientes está en
[`backend/docs/CONTRATO-API.md`](backend/docs/CONTRATO-API.md) — es la
fuente de la verdad; cualquier cambio de API se decide ahí primero, antes de
tocar código de cliente. Cómo se despliega el servidor, en
[`backend/docs/DESPLIEGUE.md`](backend/docs/DESPLIEGUE.md).

## Por dónde empezar

### Backend (hace falta que esté corriendo para probar cualquiera de los dos clientes)

```
cd backend
npm install
.\probar-local.ps1     # arranca en el puerto 8090, con PERMITIR_LOGIN_DEV=true
```

`probar-local.ps1` invita automáticamente el correo de pruebas
`prueba@local.test` y deja el servidor escuchando en `0.0.0.0:8090` (para que
el móvil, en la misma red, pueda llegar a él). El puerto real de despliegue
es el 8081 por defecto (`backend/README.md`); el script de pruebas locales lo
cambia a 8090 a propósito, que era para no chocar con Metro, el bundler de
Expo, que también usaba el 8081. Ya no hay Expo, pero el 8090 se queda: está
escrito en sitios que no se enteran de este cambio.

No hay secretos reales en este repositorio: en local, `PERMITIR_LOGIN_DEV`
salta el login de Google/Apple con solo un correo. Ninguno de los dos clientes
lo usa ya en su interfaz —los dos entran con Google—, así que solo sirve para
probar contra un servidor sin credenciales OAuth reales.

### app-ios-nativa

Hace falta un Mac con Xcode 27. Las pruebas de lógica no necesitan simulador
y tardan milisegundos:

```
cd app-ios-nativa
./verificar          # tipos, formato, pruebas de los paquetes y de interfaz
```

Para instalarla en un iPhone hay que firmar, y eso tiene su propia
documentación porque aquí se hace **sin abrir Xcode y por SSH**:
[`docs/FIRMA-SIN-PANTALLA.md`](app-ios-nativa/docs/FIRMA-SIN-PANTALLA.md).
Los identificadores, las capacidades y los perfiles se manejan por la API de
App Store Connect con `herramientas/token-appstore.py`.

Antes de tocar la interfaz, lee
[`docs/ACCESIBILIDAD.md`](app-ios-nativa/docs/ACCESIBILIDAD.md): lo que hay
ahí está medido en un iPhone de verdad con VoiceOver, no deducido de la
documentación de Apple.

### app-windows

```
cd app-windows
python -m venv venv
venv\Scripts\activate
pip install -r requirements-dev.txt
python -m guardar_enlaces
```

Por defecto apunta a `http://localhost:8081`; contra el backend local
(puerto 8090) hace falta `GUARDAR_ENLACES_API=http://localhost:8090` antes
de arrancar, o usar `app-windows/probar-local.ps1`, que ya lo fija.

## Backend compartido en el servidor (api.jmortiz.es)

Además de correr el backend en local, hay una instancia compartida en
`https://api.jmortiz.es` (Podman rootless, contenedor `guardar-enlaces`,
mismo `Dockerfile` de este repo) para que los tres probéis contra los mismos
datos. El alta es abierta: entrar con Google o Apple crea la cuenta sola.

**Estado comprobado el 2026-09-18**, contra el servidor:

- **Google funciona.** `/auth/iniciar?proveedor=google` redirige con un
  `client_id` real y `redirect_uri=https://api.jmortiz.es/auth/callback/google`.
  Se puede probar el inicio de sesión de verdad contra este servidor.
- **`POST /auth/dev-login` responde 404**, así que está desplegado con
  `PERMITIR_LOGIN_DEV=false`. Hasta el 2026-09-09 no era así, y este documento
  avisaba de que cualquiera que diera con la URL podía crearse una cuenta con
  el correo que quisiera. Ya no: esa puerta está cerrada.
- **Apple sigue sin configurar.** `/auth/iniciar?proveedor=apple` redirige con
  `client_id=x`, así que ese inicio de sesión no puede funcionar. Hace falta
  antes de publicar en la App Store: Apple exige ofrecer su inicio de sesión a
  quien ofrezca el de Google.

Si vuelve a hacer falta comprobarlo, se ve sin tocar nada ni crear cuentas:

```
curl -sSI "https://api.jmortiz.es/auth/iniciar?proveedor=google&modo=deeplink&estado=x&esquema=guardarenlaces"
curl -sS -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" -d "{}" https://api.jmortiz.es/auth/dev-login
```

## Verificar antes de dar nada por hecho

Cada proyecto tiene su propio `verificar` (tipos + lint + tests):

```
cd backend    && npm run verificar
cd app-ios-nativa && ./verificar
cd app-windows && pytest
```

Ninguno de los dos clientes se puede dar por "funciona de verdad" solo con
esto: hace falta probarlo en un dispositivo/lector de pantalla real. Decir
qué se ha comprobado y qué no (compila vs. funciona) es parte del trabajo,
no un detalle.

## Aviso importante para quien se une al proyecto

Las convenciones generales de desarrollo iOS/RN sin Mac y la guía de
accesibilidad (`GUIA-ACCESIBILIDAD-RN.md`, componentes reutilizables en
`comun/`) **viven en el repositorio padre `desarrollo-ios-rn`, no en este
repositorio**. Si no tienes acceso a ese repo, pide que te lo compartan
también — bastante de lo que hay en `app-ios-nativa/` da por hecho haber leído esa
guía antes de tocar interfaz.
