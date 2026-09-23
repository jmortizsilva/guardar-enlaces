# Guárdalo — Android

Cliente Android en Kotlin y Jetpack Compose. Qué se decidió y por qué, y lo
que falta, está en [`PLAN.md`](PLAN.md).

El contrato de API es `../backend/docs/CONTRATO-API.md`, igual que para los
otros dos clientes.

## Desarrollo

Se trabaja por línea de comandos, sin Android Studio:

```
./verificar                 # formato, Lint y pruebas. Si esto no pasa, no está terminado
./instalar                  # compilar, instalar en el teléfono y abrir la app
./gradlew :dominio:test     # solo la lógica pura
```

Hace falta el JDK 21 (`brew install openjdk@21`) y el Android SDK en
`~/Library/Android/sdk`, con `platform-tools` y `platforms;android-37.0`.
`verificar` e `instalar` los buscan ahí si no están `JAVA_HOME` y
`ANDROID_HOME`; para lanzar `./gradlew` a mano hay que exportarlos antes.

Gradle necesita saber dónde está el SDK, en un `local.properties` que no se
sube al repositorio:

```
sdk.dir=/Users/<usuario>/Library/Android/sdk
```

## Estructura

- `dominio/` — Kotlin puro: sin Android, sin red y sin base de datos. Todo lo
  que decide algo vive aquí y se prueba en la JVM del Mac.
- `fontaneria/` — SQLite, HTTP y sesión. Sigue siendo Kotlin sin Android:
  el Keystore y el SQLite del sistema los pone la app, y aquí se prueban con
  dobles y con el SQLite empaquetado.
- `app/` — la app: pantallas y cableado. Sus pruebas de interfaz corren en la
  JVM con Robolectric y leen el árbol de semántica; lo que se oye con TalkBack
  se comprueba en el teléfono.

## Lo que todavía no está

Fases 1 a 6 del plan. Ahora mismo esto es andamiaje: la app arranca, enseña
su título y enlaza con `dominio`, nada más.
