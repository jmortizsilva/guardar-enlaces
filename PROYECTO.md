# Guardar enlaces — descripción del proyecto

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
teléfono. La app de Windows sí necesita cuenta: es la que sincroniza.

## Estructura

Un solo repositorio por comodidad (todo centralizado en GitHub), pero
**tres proyectos independientes por dentro**: no comparten código, cada uno
con su propio gestor de paquetes, sus propias dependencias y su propio ciclo
de vida. Cuando cambies algo en uno, no asumas que el otro se entera.

| Carpeta | Qué es | Stack |
|---|---|---|
| [`backend/`](backend/README.md) | Servidor: auth (Google/Apple, sin contraseñas), metadatos de URLs, sincronización | Node + Fastify + SQLite |
| [`app-ios/`](app-ios/) | Cliente iOS | Expo / React Native (SDK 56) |
| [`app-windows/`](app-windows/README.md) | Cliente de escritorio | Python + wxPython |

El **contrato de API** entre el backend y los dos clientes está en
[`backend/docs/CONTRATO-API.md`](backend/docs/CONTRATO-API.md) — es la
fuente de la verdad; cualquier cambio de API se decide ahí primero, antes de
tocar código de cliente.

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
cambia a 8090 a propósito, para no chocar con el puerto por defecto de Metro
(el bundler de Expo, también 8081) cuando se prueba `app-ios/` en la misma
máquina.

No hay secretos reales en este repositorio: en local, `PERMITIR_LOGIN_DEV`
salta el login de Google/Apple con solo un correo. Ninguno de los dos clientes
lo usa ya en su interfaz —los dos entran con Google—, así que solo sirve para
probar contra un servidor sin credenciales OAuth reales.

### app-ios

```
cd app-ios
npm install
npx expo start --dev-client
```

Requiere una **development build de EAS** instalada en el iPhone — Expo Go
del App Store no sirve aquí (a fecha de este documento su versión pública va
por detrás del SDK de Expo que usa el proyecto; puede que ya se haya
puesto al día). Para instalarla en un iPhone nuevo:

```
npx eas-cli device:create      # registra el dispositivo (pide cuenta de Apple Developer)
npx eas-cli build --platform ios --profile development
```

La cuenta de Expo (`jmortizsilva`) y el proyecto EAS ya existen
(`app-ios/eas.json`, `app-ios/app.json`); pide acceso al proyecto EAS en
[expo.dev](https://expo.dev/accounts/jmortizsilva/projects/guardar-enlaces)
si vas a compilar tú mismo.

Antes de escribir código nuevo aquí, mira `app-ios/AGENTS.md` (versión
exacta del SDK) y, si tienes acceso, la documentación de accesibilidad
compartida — ver aviso más abajo.

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

**Riesgo abierto, ahora mayor que antes:** ese backend corre con
`PERMITIR_LOGIN_DEV=true` y las credenciales de Google/Apple todavía en
placeholder (`x`), porque no hay OAuth real configurado aún — comprobado el
2026-09-09: `/auth/iniciar` redirige a Google con `client_id=x`, así que el
login real **no puede funcionar todavía** contra este servidor.

Eso deja activa `POST /auth/dev-login`, que da sesión con solo escribir un
correo, sin contraseña. Antes eso lo acotaba la lista de invitados; desde que
el alta es abierta, **cualquiera que dé con la URL puede crearse una cuenta
con el correo que quiera** y usar el servidor. **Hay que desplegar con
`PERMITIR_LOGIN_DEV=false` en cuanto haya credenciales reales de Google**, y
quitar esta nota.

## Verificar antes de dar nada por hecho

Cada proyecto tiene su propio `verificar` (tipos + lint + tests):

```
cd backend    && npm run verificar
cd app-ios    && npm run verificar
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
también — bastante de lo que hay en `app-ios/` da por hecho haber leído esa
guía antes de tocar interfaz.
