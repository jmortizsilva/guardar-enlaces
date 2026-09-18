# Pasar la app de iPhone a nativo

Estado: fases 0, 1 y 2 terminadas (2026-09-18).

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
| Convivencia | Carpeta y rama aparte | `app-ios/` sigue funcionando hasta que esta esté probada en un iPhone |

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
- [ ] **3. Interfaz.** Lista, detalle, añadir, gestión de etiquetas, ajustes,
      login. Los textos de cada pantalla se escriben y se revisan juntos,
      antes de la pantalla.
      - [x] Lista de enlaces, con búsqueda, filtro, acciones y eliminación.
      - [x] Elegir etiquetas de un enlace.
      - [x] Pruebas de interfaz contra el árbol de accesibilidad (11).
      - [x] Añadir enlace, con el comportamiento de Windows: un enlace
            repetido actualiza el que había en vez de duplicarse, y guardar no
            espera a la comprobación.
      - [ ] Ajustes y login.
      - [ ] Detalle de un enlace.
      - [ ] Gestionar etiquetas (renombrar y eliminar en todos los enlaces).
- [ ] **4. Extensión de compartir** y guardado silencioso, compartiendo código
      con la app. Mueren el plugin de 94 líneas y las 315 de Swift con
      marcadores de posición.
- [ ] **5. Firma, TestFlight y prueba en un iPhone real.**

## Cabos sueltos

- **El identificador es temporal.** `com.jmortizsilva.guardarenlaces.nativa`,
  para poder tener las dos apps instaladas a la vez en el mismo iPhone y
  compararlas. En la fase 5 pasa a ser el de siempre,
  `com.jmortizsilva.guardarenlaces`. El App Group no depende de esto, así que
  puede seguir siendo el mismo.
- **El llavero de verdad solo se puede comprobar en el simulador o en el
  teléfono.** `CredencialesKeychain` no se ejercita con `swift test` en el
  Mac (necesita la autorización de llavero de la app firmada), así que la
  lógica de sesión se prueba con un llavero de mentira y el de verdad queda
  pendiente de la fase 3.
- **No hay firma configurada en este Mac** (`security find-identity` no
  encuentra ninguna identidad). Para el simulador no hace falta; para las
  fases 4 y 5 sí.
- **El login de Google no se puede probar contra `api.jmortiz.es`** mientras
  siga con `client_id=x`, según dice `PROYECTO.md`. Habrá que probarlo contra
  el backend local.
