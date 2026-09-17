# Pasar la app de iPhone a nativo

Estado: fase 0 terminada (2026-09-17).

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

2. **El mismo botón hace cosas distintas en cada app.** Desde `6942faf`, en
   Windows guardar un enlace repetido **actualiza el que ya había**; en iOS
   avisa y ofrece «Guardar de todas formas», que crea un segundo. Hay que
   decidir cuál de los dos es el bueno al llegar a esa pantalla, con sus
   textos delante. **Pendiente.**

## Fases

- [x] **0. Andamiaje.** Rama, paquete `Dominio`, proyecto Xcode, `verificar`.
- [ ] **1. Dominio en Swift.** Elemento, duplicados, presentación,
      sincronización, asentar cuenta, extracción de metadatos, y el modelo de
      etiquetas reservadas. Las 80 pruebas de hoy, portadas, más las que
      falten. Se verifica entero con `swift test`, sin simulador.
- [ ] **2. Fontanería.** Almacén SQLite, cliente HTTP, sesión con Keychain y
      rotación de token, sincronizador. Decidir aquí si la app nueva adopta la
      base de datos que ya hay en el teléfono o empieza limpia.
- [ ] **3. Interfaz.** Lista, detalle, añadir, gestión de etiquetas, ajustes,
      login. Los textos de cada pantalla se escriben y se revisan juntos,
      antes de la pantalla.
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
- **No hay firma configurada en este Mac** (`security find-identity` no
  encuentra ninguna identidad). Para el simulador no hace falta; para las
  fases 4 y 5 sí.
- **El login de Google no se puede probar contra `api.jmortiz.es`** mientras
  siga con `client_id=x`, según dice `PROYECTO.md`. Habrá que probarlo contra
  el backend local.
