# Pasar la app de iPhone a nativo

Estado: terminada. La app nativa sustituye a la de Expo desde el
2026-09-20.

## Por qué

La app de `app-ios/` funciona. El motivo del cambio no es el gusto, son los
sitios donde la capa de React Native es el problema, todos documentados en su
propio código:

| Dónde | Qué pasa |
|---|---|
| `src/accesibilidad/foco.ts` | `setAccessibilityFocus` no hace nada con la arquitectura nueva, y no da error |
| `src/accesibilidad/anuncios.ts` | Hay que renunciar a la prioridad de los anuncios porque depende de la versión de RN |
| `src/interfaz/CampoBusqueda.tsx` | Un `TextInput` controlado duplica el texto al dictar |
| `src/sesion/loginProveedor.ts`, `src/dominio/duplicados.ts` | El `URL` de React Native son unas expresiones regulares: en Jest pasa, porque ahí el `URL` es el de Node, y en el teléfono falla |
| `AGENTS.md` | Downgrade de SDK 57 a 56 porque Expo Go no estaba aprobado en la App Store |

Y el motivo por el que se eligió React Native ya no existe: la documentación
del repositorio padre es de «desarrollo iOS/RN **sin Mac**», y ahora hay Mac
con Xcode.

Lo que se gana, además de quitar esa capa: prioridad real en los anuncios de
VoiceOver, `accessibilityCustomContent` (que el dominio, las etiquetas y la
fecha de cada fila se lean solo si se piden por el rotor, en vez de ir
pegados al título en una sola etiqueta), `URL` de Foundation de verdad, y una
extensión de compartir que comparte código con la app en vez de duplicarlo.

## Lo que se pierde, que no es poco

**Las actualizaciones por aire.** Hoy un texto corregido llega en minutos sin
pasar por Apple; en nativo, cada cambio es una build y un TestFlight. Como los
textos que se leen en voz alta son contrato y se corrigen justo al probarlos
con VoiceOver, esto se va a notar. Se decidió aceptarlo (2026-09-17).

También desaparece la posibilidad de tener Android casi gratis, que `app.json`
dejaba preparada.

## Decisiones

| Qué | Decidido | Por qué, y qué se descartó |
|---|---|---|
| Interfaz | SwiftUI, con UIKit puntual donde haga falta | La app es lista, formulario y ajustes. UIKit daría el comportamiento del sistema sin sorpresas, pero a cambio de dos o tres veces más código, casi todo repetitivo |
| iOS mínimo | 18, compilando con el SDK 27 | Lo que esta app necesita de accesibilidad (prioridad de anuncios iOS 17, `accessibilityCustomContent` iOS 14, `@AccessibilityFocusState` iOS 15) ya está en 18: fijar 26 o 27 no habría aportado accesibilidad, solo habría dejado fuera iPhones |
| Persistencia | SQLite del sistema, mismo esquema que hoy | SwiftData impone su ciclo y su historial, y el modelo de sincronización de aquí (outbox explícito, lápidas, cursor) no encaja. GRDB es cómoda, pero es una dependencia grande para cuatro tablas |
| Proyecto Xcode | Carpetas sincronizadas (Xcode 16+) | El `.pbxproj` deja de cambiar al añadir ficheros, sin meter XcodeGen ni Tuist |
| Código compartido | Paquete Swift local | La app y la extensión dependen del mismo paquete. Sin framework aparte |
| Formato y lint | `swift-format`, el que trae Xcode | Sin instalar nada. SwiftLint se descarta mientras esto baste |
| Alcance | Paridad + lo que iOS no tiene y Windows sí | Ver más abajo |
| Convivencia | Carpeta y rama aparte | La de Expo siguió funcionando hasta que esta estuvo probada en un iPhone. Retirada el 2026-09-20 |

## Dos divergencias, que no son solo traducir

1. **Etiquetas reservadas.** El commit `eec3fab` las dejó en backend y Windows
   con la nota «iOS queda para la siguiente entrega». Hay que traer el modelo
   `EtiquetaDefinida`, su tabla, su propia cola de pendientes,
   `etiquetasDefinidas` en el pull y en el push, y la pantalla de gestión
   (renombrar y eliminar en todos los enlaces, con recuento).

2. **El mismo botón hacía cosas distintas en cada app.** Desde `6942faf`, en
   Windows guardar un enlace repetido actualiza el que ya había; en iOS avisaba
   y ofrecía «Guardar de todas formas», que creaba un segundo. **Resuelto el
   2026-09-18:** manda el de Windows, y además guardar deja de esperar a la
   comprobación. Los dos clientes hacen por fin lo mismo.

## Fases

- [x] **0. Andamiaje.** Rama, paquete `Dominio`, proyecto Xcode, `verificar`.
- [x] **1. Dominio en Swift.** Elemento, duplicados, presentación,
      sincronización, asentar cuenta, extracción de metadatos y el modelo de
      etiquetas reservadas, con 73 pruebas que corren en el Mac en seis
      milisegundos. Tres cosas cambiaron respecto al original, y no por
      gusto: `asentarCuenta` es ahora una función pura que dice qué hay que
      hacer, en vez de recibir el almacén y una promesa (se prueba sin
      simulacros); la lista desempata por identificador cuando dos enlaces
      comparten fecha, porque un diccionario de Swift no promete orden y la
      lista se recolocaría sola entre dos aperturas; y la dirección del
      oEmbed de YouTube se escapa a mano, porque `URLComponents` deja pasar
      los dos puntos y las barras.
- [x] **2. Fontanería.** Paquete aparte, con 38 pruebas más: almacén SQLite
      (mismo esquema que Windows, tabla por tabla), cliente HTTP, sesión con
      llavero y rotación de token, y el ciclo de sincronización con las
      etiquetas dentro. El cliente se prueba entero contra un servidor de
      mentira metido por debajo de `URLSession`, sin red y sin backend.
      **Decidido:** la app nueva arranca vacía; no se importa la base de datos
      de la app de Expo, porque con cuenta la trae el servidor y en la fase 5,
      al recuperar el identificador de siempre, se encontrará la que ya había.
- [x] **3. Interfaz.** Lista, detalle, añadir, gestión de etiquetas, ajustes,
      login. Los textos de cada pantalla se escriben y se revisan juntos,
      antes de la pantalla.
      - [x] Lista de enlaces, con búsqueda, filtro, acciones y eliminación.
      - [x] Elegir etiquetas de un enlace.
      - [x] Pruebas de interfaz contra el árbol de accesibilidad (20).
      - [x] Añadir enlace, con el comportamiento de Windows: un enlace
            repetido actualiza el que había en vez de duplicarse, y guardar no
            espera a la comprobación.
      - [x] Ajustes y login. Sin «Buscar actualizaciones» (no hay
            actualizaciones por aire) y con la versión instalada en su sitio,
            para saber qué build llegó por TestFlight. El interruptor de
            guardado silencioso no aparece hasta que exista su extensión, en
            la fase 4.
      - [x] Detalle de un enlace. Se llega por una acción nueva del rotor,
            «Ver detalles»: tocar la fila sigue abriendo en modo lector, que
            es lo que ya estaba decidido.
      - [x] Gestionar etiquetas: renombrar y eliminar en todos los enlaces a
            la vez, crear reservadas, y cada fila diciendo cuántos enlaces
            lleva.
- [x] **4. Extensión de compartir** y guardado silencioso, compartiendo código
      con la app. Mueren el plugin de 94 líneas y las 315 de Swift con
      marcadores de posición.
      - [x] La base de datos se muda a la carpeta del App Group, en modo
            `WAL` y con espera, que es lo que hace falta para que dos
            procesos escriban en ella sin pisarse.
      - [x] `GuardarEnlace`: un solo camino para guardar, el de la pantalla
            de añadir y el de la extensión. Antes vivía dentro de
            `ModeloApp`, donde la extensión no lo veía.
      - [x] El objetivo `Compartir` en el proyecto, escrito a mano como el
            resto, con la hoja en SwiftUI y el interruptor «Guardar sin
            preguntar» en Ajustes.
      - [ ] Firmar: falta asociar el App Group al identificador en el portal.
            Es lo único de todo esto que no se puede hacer por línea de
            comandos (ver `docs/EXTENSION-COMPARTIR.md`).
      - [x] Firmada e instalada en el teléfono. Hizo falta pasar a firma
            manual: la automática necesita que `xcodebuild` entre en el
            portal, y eso aquí no funciona.
      - [x] Probada en el teléfono el 2026-09-20. Salió una sola cosa, y era
            una incoherencia: en la aplicación se puede crear una etiqueta al
            vuelo al guardar un enlace, y en la hoja de compartir no se podía.
            Ahora sí, con el mismo campo y el mismo botón.
- [x] **5. Firma e instalación en el iPhone.** Se adelantó a la fase 3,
      porque el teléfono se puede conectar a este Mac y así cada cambio de
      interfaz se oía el mismo día en vez de al final. Todo lo que costó está
      en `docs/FIRMA-SIN-PANTALLA.md`.
- [x] **6. Sustituir a la de Expo** (2026-09-20). El identificador, el
      esquema, el nombre visible y la clave del guardado silencioso vuelven a
      ser los de siempre, y la carpeta `app-ios/` sale del repositorio: dos
      clientes de iOS conviviendo es la forma segura de que dentro de tres
      meses nadie sepa cuál manda. Sigue entera en el historial.

      Lo que esto cuesta, y se sabía: al cambiar el identificador cambia el
      grupo de llavero, así que **la sesión guardada no se lee y hay que
      volver a entrar una vez**. Los enlaces no se pierden, están en el
      servidor y en la carpeta del App Group, que es la misma.

## Cabos sueltos

- **El llavero nunca se prueba solo.** `CredencialesKeychain` no se ejercita
  con `swift test` en el Mac (necesita la autorización de llavero de una app
  firmada), y entrar con Google o con Apple abre una hoja del sistema que una
  prueba de interfaz no puede recorrer. La lógica de sesión se prueba con un
  llavero de mentira, y el de verdad solo se comprueba a mano en el teléfono.
  Se hizo el 2026-09-18 con las dos cuentas, incluidas la persistencia y el
  cambio de una a otra.
- **En TestFlight desde el 2026-09-20**, compilación 1 de la versión 1.0. Lo
  que costó está en `docs/TESTFLIGHT.md`. Falta lo que pasa por revisión de
  Apple: probadores externos y publicación de verdad, que piden la ficha
  entera (capturas, descripción, política de privacidad y categoría).
