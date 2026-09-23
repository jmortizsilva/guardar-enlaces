# Guárdalo en Android

Estado: en la fase 1, pendiente de revisar los textos.

## Por qué

Hoy Guárdalo existe en iPhone y en Windows. Quien usa Android no tiene
cliente, y la app de Expo, que lo habría dado casi gratis, se retiró en
septiembre de 2026 por los motivos que cuenta
[`../app-ios-nativa/PLAN.md`](../app-ios-nativa/PLAN.md): la capa de React
Native era justo lo que estorbaba en accesibilidad.

Así que Android va en nativo, Kotlin y Jetpack Compose, por la misma razón
que iOS: el lector de pantalla tiene que ser TalkBack de verdad, con sus
propias herramientas, y no lo que quede después de que una capa intermedia
traduzca lo que se pidió para VoiceOver.

Lo que ya está decidido y no se vuelve a discutir aquí, porque está resuelto
en iOS y Windows:

- El contrato es [`../backend/docs/CONTRATO-API.md`](../backend/docs/CONTRATO-API.md).
  Este cliente no pide ningún cambio en él.
- **La cuenta no es obligatoria.** La app se abre sin pedir nada, guarda en
  el teléfono y resuelve ella misma los metadatos. La cuenta solo hace falta
  para sincronizar, y al entrar se pregunta qué hacer con lo que ya había.
- **Etiquetas reservadas**, con su tabla, su cola y su pantalla de gestión.
- **Un enlace repetido actualiza el que había**: suma etiquetas y conserva el
  título si la comprobación no trae ninguno.
- **Guardar no espera a la comprobación.** Si no llega a tiempo, se guarda
  sin título y se dice.

La referencia de comportamiento es el código de `app-ios-nativa/`. La app de
Expo (`git show 464610c:app-ios/...`) solo se consulta como historia.

## Decisiones

| Qué | Decidido | Por qué, y qué se descartó |
|---|---|---|
| Lenguaje e interfaz | Kotlin y Jetpack Compose con Material 3 | Las Views clásicas darían el comportamiento de TalkBack más conocido, pero a cambio de XML, adaptadores y el doble de código. Compose genera el árbol de accesibilidad a partir de su árbol de semántica, que es además lo que leen sus pruebas (ver «Pruebas») |
| SDK mínimo | **28** (Android 9) | Lo que decide es la accesibilidad: `setHeading` y `setAccessibilityPaneTitle` son de la API 28. Por debajo, Compose los rellena igual, pero TalkBack no los recibe como encabezado ni como título de panel, y moverse por encabezados es cómo se recorre una pantalla. TalkBack se actualiza por la tienda y no depende del sistema; lo que sí depende del sistema es eso. Subir a 31 permitiría saber que lo copiado es una URL sin leerlo (ver la tabla de accesibilidad), pero dejaría fuera teléfonos de 2019-2020 por una comodidad que tiene alternativa |
| SDK de compilación y objetivo | `compileSdk` **37** y `targetSdk` **36** | 36 como objetivo es lo que exige Google Play desde el 31 de agosto de 2026 para publicar. Compilar contra la 37 no es una elección: Compose 1.12 y OkHttp 5.5, las estables de hoy, ya lo exigen. La primera versión de este plan decía que eso solo lo pediría Compose 1.13 y que se podía compilar contra la 36; al montar la fase 0, Gradle se negó (2026-09-23). Compilar contra la 37 no cambia cómo se comporta la app en el teléfono, eso lo decide `targetSdk`. Ninguno de los cambios de comportamiento de la 37 toca a esta app (lo más cercano es el endurecimiento de arrancar actividades desde segundo plano, y aquí no se hace), así que el objetivo puede subir cuando se quiera |
| Estructura | Tres módulos de Gradle: `dominio`, `fontaneria` y `app` | La misma separación que iOS. `dominio` y `fontaneria` son módulos de Kotlin puro, sin Android, y sus pruebas corren en la JVM del Mac en milisegundos. Lo que necesita Android (el Keystore, el conductor de SQLite del sistema, las pantallas) vive en `app` |
| Carpetas | `src/main/kotlin` y `src/test/kotlin`, las de Gradle | Mis reglas piden `pruebas/` junto al código, pero también seguir la convención cuando las herramientas la esperan. Gradle, el plugin de Android y cualquiera que abra el proyecto buscan `src/test`; cambiar los `sourceSets` se puede, pero rompe lo que todo el mundo espera encontrar ahí. Los paquetes y los ficheros, en español |
| Identificador | `com.jmortizsilva.guardarenlaces` | El mismo que iOS. En Android no choca con nada y así se reconoce |
| Persistencia | SQLite a mano, con el mismo esquema que Windows e iOS, tabla por tabla y columna por columna | **Room se descarta** por tres motivos: añade su propia tabla (`room_master_table`), así que el esquema dejaría de ser el mismo; pide KSP y código generado para cuatro tablas y seis consultas; y su gracia (entidades, observar consultas) no encaja con el modelo de sincronización de aquí, que es outbox explícito, lápidas y cursor, igual que SwiftData en iOS. **SQLDelight** se descarta por lo mismo que GRDB: dependencia y generador de código para muy poco |
| Cómo se habla con SQLite | La interfaz `SQLiteDriver` de `androidx.sqlite`: en el teléfono, `AndroidSQLiteDriver` (el SQLite del sistema, sin librería nativa añadida); en las pruebas, `BundledSQLiteDriver` | Es lo que permite probar el almacén en la JVM del Mac sin Robolectric y sin emulador: el mismo SQL, el mismo envoltorio y dos conductores. La alternativa sin dependencias, `SQLiteOpenHelper` a pelo, obliga a probar el almacén con Robolectric, que tarda segundos en arrancar. Comprobado en la fase 0: el conductor empaquetado carga en este Mac (arm64) |
| Dos procesos | No hay | En iOS la extensión de compartir es otro programa, y eso obligó al App Group, al modo `WAL` y a marcar la protección de los ficheros. En Android, la pantalla de compartir es una actividad más de la misma app, en el mismo proceso, y abre el mismo almacén. Se deja `WAL` igual, porque la sincronización escribe mientras la lista lee |
| Cliente HTTP | OkHttp | Tiempos de espera, reintentos de conexión y HTTP/2 hechos y probados. `HttpURLConnection` no añade dependencias, pero habría que reescribir a mano lo que OkHttp ya trae, y probarlo. Retrofit y Ktor se descartan: son capas encima para ocho rutas |
| JSON | `kotlinx.serialization` | Se decodifica en tiempo de compilación, con valores por defecto para lo que falte, que es lo que hacen los `init(from:)` tolerantes de iOS: una baja que llega del servidor solo trae identificador y fecha. `org.json`, que viene en Android, no sirve en las pruebas de la JVM: ahí es un simulacro que lanza «Method not mocked» |
| Servidor de mentira | `MockWebServer` de OkHttp, solo en las pruebas | Es un servidor HTTP de verdad en `localhost`, sin red y sin backend. Comprueba cabeceras, cuerpos y códigos igual que el `ServidorFalso` de iOS, y además que lo que sale por el cable es JSON válido |
| Sesión | Token de acceso en memoria; token de refresco cifrado con una clave del **Android Keystore** | Ver «La sesión», más abajo |
| Entrar con Google y con Apple | Por web, con `/auth/iniciar` en modo `deeplink`, abierto en una **Auth Tab** | Ver «Entrar con una cuenta», más abajo |
| Navegación | Una sola actividad y una pila de pantallas propia, con `BackHandler` | Son seis pantallas. Navigation Compose se valorará en la fase 3 si la pila propia se complica; lo que importa aquí es que el gesto de atrás de TalkBack haga siempre lo que se espera, y eso hay que medirlo con cualquiera de las dos |
| Hojas | Pantallas completas y `AlertDialog`; nada de `ModalBottomSheet` | Una hoja inferior de Compose tiene su propio arrastre y su propia gestión del foco, y no sé cómo se comporta con TalkBack. Una pantalla completa y un diálogo son ventanas que TalkBack ya sabe tratar. Hasta medir la hoja en el teléfono, no se usa |
| Textos | Un objeto `Textos` en `dominio`, no `strings.xml` | Igual que en iOS, y por lo mismo: se revisan todos juntos y se prueban en la JVM. Los recursos de Android necesitan un `Context`, y en una prueba eso es Robolectric. La app está solo en español, así que no se pierde la traducción |
| Formato y análisis | Avisos del compilador como errores, Android Lint con avisos como errores, `ktfmt`, y `animal-sniffer` en los módulos sin Android | `ktfmt` formatea sin opciones que discutir, como `swift-format`. ktlint y detekt se descartan: más reglas que configurar para un proyecto de este tamaño. `animal-sniffer`, con las firmas de la API 28 de gummy-bears, se añadió en la fase 1: es lo único que avisa si `dominio` o `fontaneria` usan algo del JDK que Android 9 no tiene. Lint no lo hace en un módulo sin Android, comprobado. OkHttp se protege igual |
| Compilar e instalar | `./gradlew` y `adb`, sin Android Studio | Ver «Compilar, firmar e instalar» |

### La sesión

- **El token de acceso** vive solo en memoria, como en iOS y en Windows.
- **El token de refresco** se cifra con AES-GCM usando una clave que se crea
  dentro del Android Keystore y no sale de él. El resultado cifrado se guarda
  en un fichero privado de la app.
- La clave se pide con `setUnlockedDeviceRequired(true)` (API 28): el
  equivalente a `kSecAttrAccessibleWhenUnlocked` de iOS. Compartir un enlace
  exige tener el teléfono desbloqueado, así que no estorba.
- **`EncryptedSharedPreferences` se descarta**: la librería
  `androidx.security:security-crypto` está marcada entera como obsoleta desde
  junio de 2025, y la propia nota recomienda usar el Keystore directamente.
- **La copia de seguridad de Android excluye ese fichero** (reglas de
  `dataExtractionRules`). Si se restaurara en otro teléfono, la clave no iría
  con él y el token sería basura; mejor que no llegue. La base de datos sí se
  copia: así, quien usa la app sin cuenta no pierde sus enlaces al cambiar de
  teléfono. Esto último es una decisión que se puede discutir.
- Si el Keystore no puede descifrar (clave perdida, restauración a medias),
  se trata como «no hay sesión» y se borra el fichero, que es lo que hace iOS
  cuando el token no sirve.

**La rotación.** Cada `POST /auth/renovar` invalida el token usado y devuelve
otro. El nuevo se guarda **antes** de devolver nada, porque perderlo deja la
sesión sin forma de renovarse. Y las renovaciones se hacen **de una en una**,
con un `Mutex` de corrutinas: si dos peticiones reciben un 401 a la vez (la
comprobación de un enlace y una sincronización al volver a la app), la
segunda espera, ve que el token de acceso ya cambió y lo usa en vez de
renovar otra vez con un token de refresco que ya está revocado.

Esto último **no lo hace iOS**, o no del todo: `Sesion` es un actor, pero un
actor de Swift deja entrar otra llamada mientras la primera espera a la red.
Dos 401 simultáneos harían dos renovaciones con el mismo token; la segunda
fallaría, y `restaurar` borra el token guardado cuando falla. No lo he
comprobado en el teléfono; lo apunto en «Cabos sueltos» para mirarlo aparte.

### Entrar con una cuenta

**Google** va por web, igual que Windows y que Google en iOS: se abre
`/auth/iniciar?proveedor=google&modo=deeplink&esquema=guardarenlaces` y el
servidor hace todo el intercambio.

Se abre en una **Auth Tab** (`AuthTabIntent`, de `androidx.browser` 1.9 en
adelante, con Chrome 137 o posterior). Es lo más parecido que tiene Android a
`ASWebAuthenticationSession`: el navegador captura él mismo la vuelta a
`guardarenlaces://auth-callback?codigo=…` y se la devuelve a la app con un
`ActivityResultCallback`, sin pasar por el sistema de intents. Eso importa en
Android más que en iOS: cualquier app puede declarar el esquema
`guardarenlaces`, y con un deep link normal el código de canje podría acabar
en ella.

Cuando el navegador no admite Auth Tab, la propia librería abre una Custom
Tab, y ahí la vuelta sí llega por un intent. Para ese caso la actividad
declara el esquema y solo acepta la vuelta si hay un inicio de sesión en
curso, lanzado por ella. Eso evita que un enlace de fuera meta a alguien en
una cuenta ajena, pero no que otra app se quede con el código. Cerrarlo del
todo pide cambiar el contrato (PKCE, o un App Link por `https` con
`assetlinks.json`); se apunta y no se toca ahora.

**Descartado: el inicio de sesión nativo de Google con Credential Manager.**
Saca una hoja del sistema sin navegador, que con TalkBack es más corta, pero
devuelve un token de identidad de Google que el servidor hoy no sabe canjear:
haría falta una ruta nueva, como `/auth/apple-nativo`. Si la Auth Tab resulta
incómoda con TalkBack, es lo siguiente que habría que proponer, empezando por
el contrato.

**Apple** no tiene inicio de sesión nativo en Android. Va por web, con
`proveedor=apple`, igual que en Windows. Dos avisos:

- El servidor compartido **todavía no tiene Apple configurado**
  (`client_id=x`, comprobado el 2026-09-18 según `PROYECTO.md`). Hasta que lo
  esté, este botón no se puede probar de verdad.
- Para que sea la misma cuenta que en el iPhone, el Services ID tiene que
  estar agrupado bajo el identificador de la app de iOS (ver el contrato).
  Android usa el mismo flujo web que Windows, así que no añade nada nuevo.

### Compartir desde otras apps

Una actividad propia, `ActividadCompartir`, que recibe `ACTION_SEND` con
`text/plain`. Chrome manda la dirección en `EXTRA_TEXT` y el título en
`EXTRA_SUBJECT`; otras apps mandan una frase con la dirección dentro, y para
eso está ya `Enlaces.direccionDentroDe`, que se trae tal cual.

- **Un solo camino para guardar.** `GuardarEnlace` y `CrearEtiqueta` viven en
  `fontaneria` y los llaman igual la pantalla de añadir y la de compartir,
  como en iOS. Aquí es más fácil: es el mismo proceso y el mismo almacén.
- **Con «Guardar sin preguntar» apagado**, se enseña una pantalla con la
  dirección, las etiquetas, el campo para crear una al vuelo, y Guardar y
  Cancelar. La misma que en iOS.
- **Con el interruptor encendido**, la actividad no enseña nada: comprueba la
  página con el mismo límite de dos segundos, guarda, intenta subir con un
  límite de tres, y se cierra. El acuse se da con un **`Toast`**, que TalkBack
  lee y que es lo que hacen las apps de Android al guardar desde el menú de
  compartir. Un anuncio lanzado desde una actividad que se está cerrando se
  pierde casi seguro. **A medir en el teléfono.**
- En Android, cerrar la actividad no mata el proceso en el acto, como pasa
  con la extensión en iOS. Aun así, se sube antes de cerrar y con límite, y
  lo que no dé tiempo queda en la cola: WorkManager daría la subida en segundo
  plano garantizada, pero es otra dependencia para algo que iOS tampoco hace.

### Compilar, firmar e instalar

Todo por línea de comandos, como en iOS:

```
./verificar                        # formato, Lint, pruebas de la JVM
./gradlew :dominio:test            # solo la lógica pura
./gradlew installDebug             # compilar e instalar en el teléfono conectado
adb shell am start -n com.jmortizsilva.guardarenlaces/.ActividadPrincipal
```

- **La firma de depuración** la genera Gradle sola la primera vez, en
  `~/.android/debug.keystore`. Con eso basta para instalar en el teléfono.
- **La firma de publicación** es un almacén creado con `keytool` (viene con el
  JDK), guardado fuera del repositorio con la clave en un fichero aparte, como
  el llavero de firma de iOS. Gradle lo lee de `~/.gradle/gradle.properties`.
  Si se pierde, en Google Play no se puede actualizar la app: hay que decidir
  dónde se guarda la copia antes de publicar nada.
- **El teléfono** se conecta por USB o por depuración inalámbrica
  (`adb pair`, Android 11 o posterior). La inalámbrica encaja mejor con
  trabajar por SSH, porque no hace falta que el teléfono esté enchufado a
  este Mac.

## La accesibilidad, de VoiceOver a TalkBack

La app de iOS resolvió varias cosas midiéndolas en un iPhone. Esta tabla dice
qué hay en Android para cada una. **Nada de la columna de Android está medido
todavía**: sale de la documentación actual y de cómo funciona TalkBack, y se
comprueba en el teléfono en la fase 3, igual que se hizo en iOS. Lo que se
oiga se anota en `docs/ACCESIBILIDAD.md`.

Antes de la tabla, dos cosas que he visto al leer el código de iOS y que
cambian lo que hay que traducir:

- **`accessibilityCustomContent` no se usa en la app de iOS.** El plan de iOS
  lo pone como una de las ganancias, pero la fila de la lista compone una sola
  etiqueta con todo: «título. dominio — etiquetas — fecha»
  (`PantallaLista.swift`, `FilaEnlace`). No hay nada que se lea «solo si se
  pide».
- **La app de iOS tampoco devuelve el foco a mano.** No usa
  `@AccessibilityFocusState` en ninguna pantalla; se fía de lo que haga
  SwiftUI. Quien lo hacía a mano era la de Expo (`foco.ts`).

| En iOS | Qué hacía | En Android | Equivalente directo |
|---|---|---|---|
| Acciones del rotor en la fila (`accessibilityActions`): Ver detalles, Editar etiquetas, Copiar URL, Eliminar | Todo lo que se hace con un enlace, desde la propia fila, sin abrir nada | `Modifier.semantics { customActions = … }` con `CustomAccessibilityAction`. TalkBack las ofrece en el menú de acciones (deslizar arriba y a la derecha, o tocar con tres dedos) y en los controles de lectura, eligiendo «Acciones» y deslizando arriba o abajo, que es lo más parecido al rotor | Sí |
| Las acciones, **una sola vez**: nada de `swipeActions`, que las duplicaban | El rotor no se ensucia | La trampa equivalente en Android es la **pulsación larga**: `combinedClickable` con `onLongClick` añade «mantener pulsado» a las acciones de TalkBack. No se usa, ni `SwipeToDismissBox`. Para quien mira la pantalla, un botón «Más opciones» en la fila con el mismo menú, **oculto a TalkBack** (`clearAndSetSemantics {}`), porque TalkBack ya tiene esas acciones en la fila. Es la misma regla de iOS: el menú para el tacto y las acciones desde una sola fuente | Sí, con otra trampa |
| El orden de las acciones, **declarado al revés** porque VoiceOver las lee al revés | Se oyen en el orden que se quiere | No hay nada documentado sobre el orden en TalkBack. Se declaran en el orden en que se quieren oír y **se mide** | A medir |
| El toque en la fila abre, con «Abrir en modo lector» como pista | Doble toque = abrir | `Modifier.clickable(onClickLabel = …)`: TalkBack dice «toca dos veces para …» con esa etiqueta. **Pero no hay modo lector**: una Custom Tab no se puede abrir en modo lector desde la app. El texto tiene que decir lo que pasa de verdad (propuesta: «Abrir»), y eso es una diferencia de textos, no una traducción | Sí; el texto cambia |
| `accessibilityCustomContent`, que en iOS **no se usa** (ver arriba) | — | TalkBack **no tiene** contenido que se lea solo si se pide. Lo que hay: la etiqueta (siempre), `stateDescription` (siempre, detrás) y las acciones. Opciones: **(a)** lo mismo que hace iOS hoy, todo en la etiqueta con el título primero, que al deslizar corta la lectura; **(b)** solo el título en la etiqueta, y lo demás en la pantalla de detalle. **Lo decides tú** antes de la fase 3 | No |
| Prioridad de los anuncios: `importante` (alta) e `informativo` (normal), con `accessibilitySpeechAnnouncementPriority` | Un resultado se oye entero aunque cambie el foco | `announceForAccessibility` y los eventos `TYPE_ANNOUNCEMENT` están **obsoletos desde Android 16**. Lo que recomienda Android: **(1)** para un cambio de pantalla, `paneTitle`; **(2)** para un cambio importante, una **región viva**, `liveRegion = Assertive` (corta lo que se esté leyendo) o `Polite` (espera); **(3)** para un error de un campo, la semántica `error`. Propuesta: el mismo `Anuncios` de dos funciones, hecho con una línea de estado visible en la pantalla con región viva, `Assertive` para `importante` y `Polite` para `informativo`. Dos trampas conocidas: una región viva solo habla cuando **cambia** su texto, así que el mismo aviso dos veces seguidas («URL copiada») no suena si no se vacía antes; y no se sabe si TalkBack lee una región viva que no se ve. **Se mide.** Si no alcanza, el recurso es la API obsoleta, que sigue funcionando, apuntando el motivo | Parcial |
| Un anuncio al abrir una pantalla se pierde; iOS espera 900 ms | «Hay un enlace copiado, puedes pegarlo» | Cada pantalla lleva su `paneTitle` y TalkBack la anuncia al entrar. El aviso del enlace copiado va en un texto visible junto al botón de pegar, con región viva `Polite`, para que espere a que acabe el título. El retraso de iOS no se copia sin medirlo | A medir |
| Si un control aparece solo, se dice | El botón de pegar | Igual, con la línea anterior. **Diferencia:** en Android 12 o posterior leer el portapapeles saca un aviso del sistema («Guárdalo ha pegado…»), y no hay botón de pegar del sistema como `PasteButton`. Se puede saber si hay texto copiado sin leerlo (la descripción del portapapeles), y desde la API 31 incluso si parece una URL. Por debajo de 31, se ofrece el botón con cualquier texto copiado | Parcial |
| Devolver el foco al cerrar un diálogo o al desaparecer una fila (lo hacía la de Expo con `sendAccessibilityEvent`; la de iOS lo deja a SwiftUI) | No perder el sitio | Compose puede mover el **foco de entrada** con `FocusRequester.requestFocus()`, y TalkBack suele llevar su cursor a lo que recibe el foco de entrada, pero **no siempre**: son dos focos distintos y no hay API pública de Compose para mover el de accesibilidad. Plan: al cerrar un diálogo, pedir el foco para lo que lo abrió; al eliminar una fila, para la siguiente (o la anterior, o el mensaje de lista vacía). **Se mide.** Que el foco de entrada llega se prueba con `assertIsFocused()`; que TalkBack lo siga, solo de oído | No directo; alternativa a medir |
| Dictado en el campo de búsqueda (en la de Expo, un campo controlado duplicaba el texto al dictar) | Dictar la búsqueda funciona | El dictado en Android es el del teclado (el micrófono de Gboard), y escribe con texto en composición. El campo de Compose que trabaja con `value`/`onValueChange` tiene problemas de sincronización con el teclado documentados por Google, que son la misma familia que el fallo de Expo. Se usa el campo **basado en estado** (`TextFieldState`), que es el que Google recomienda por eso mismo. Con TalkBack, el gesto de atrás cierra el teclado, así que no hace falta el «Cancelar» que añadió la de Expo; sí uno para borrar la búsqueda. **Se prueba dictando** | Sí, eligiendo bien el campo |
| Títulos de pantalla que VoiceOver lee primero | Saber dónde se está | `paneTitle` en cada pantalla, `heading()` en su título y `Activity.setTitle`. Por eso la API 28 como mínimo | Sí |
| Confirmar antes de eliminar, con la consecuencia dicha | «Se eliminará también en el ordenador» | `AlertDialog` de Material 3: es una ventana, TalkBack lee su título y deja el foco dentro. Botones «Eliminar» y «Cancelar» | Sí |
| Pruebas contra el árbol de accesibilidad (XCUITest) | Que lo que se lee no se rompa | Pruebas de Compose contra el árbol de semántica (ver «Pruebas») | Sí, con matices |

Los textos que se leen en voz alta **son contrato**. En Android no son una
copia de los de iOS: «iPhone» pasa a ser «teléfono», «modo lector» no existe,
y lo que TalkBack añade por su cuenta («botón», «toca dos veces para…») no es
lo mismo que añade VoiceOver. Por eso, en la fase 3, **cada pantalla empieza
por su lista de textos**, que se revisa antes de escribir la pantalla.

## Pruebas

- **`dominio` y `fontaneria`**: pruebas de JUnit en la JVM del Mac, sin
  emulador ni teléfono. Las pruebas de iOS sirven de especificación (124 del
  dominio y 58 de la fontanería el 2026-09-23; su plan dice 73 y 38, que eran
  las del día en que se escribió): se traen una a una y se cuenta cuáles no tienen
  sentido aquí y por qué. El almacén se prueba con el conductor de SQLite
  empaquetado; el cliente, la sesión y el sincronizador, contra
  `MockWebServer`. El reloj y los identificadores se inyectan, como en iOS.
- **La interfaz**, con las pruebas de Compose (`createComposeRule`,
  `onNode…`, comprobaciones de etiquetas, acciones propias, encabezados y
  título de panel). Corren de dos formas: **en la JVM con Robolectric**, que
  es rápido y va en `verificar`; y **en el teléfono**, las mismas, antes de
  dar una fase por cerrada.
- **El matiz**: el árbol de semántica es de donde Compose saca lo que lee
  TalkBack, pero no es lo mismo. La traducción a `AccessibilityNodeInfo` la
  hace Compose, y ahí hay fallos que solo se ven en el teléfono. Por eso se
  prueba también en el teléfono, y por eso lo que se oye (el orden de las
  acciones, si una región viva suena, adónde va el foco) sigue siendo
  comprobación de oído, como en iOS.
- El Keystore, la Auth Tab y el menú de compartir no se pueden probar solos.
  La lógica que los rodea se prueba con dobles (credenciales en memoria, la
  vuelta del inicio de sesión como una URL), y lo de verdad se comprueba a
  mano en el teléfono.

## Fases

- [x] **0. Andamiaje.** Rama `app-android`, herramientas instaladas (ver
      «Herramientas en este Mac»), proyecto de Gradle con sus tres módulos,
      `verificar`, `instalar`, y una app vacía instalada en el teléfono.
      - [x] Proyecto, módulos, icono y `verificar` en verde: formato, Lint
            con avisos como error, 4 pruebas y el APK de depuración.
      - [x] El SQLite empaquetado carga en la JVM de este Mac, y las pruebas
            de Compose corren con Robolectric. Robolectric necesita permisos
            para tocar clases internas de Java desde el JDK 17; están en
            `app/build.gradle.kts`.
      - [x] Instalada en el teléfono (Pixel 10a, Android 17, TalkBack 17.0)
            el 2026-09-23, primero por cable y después por depuración
            inalámbrica, ya sin cable. TalkBack lee «Guárdalo, encabezado»:
            el encabezado de la API 28 llega de verdad, no solo en la prueba.
- [ ] **1. Dominio en Kotlin.** Elemento, duplicados, presentación,
      sincronización, asentar cuenta, enlaces, metadatos, login y etiquetas
      reservadas, con sus pruebas. Los `Textos` de Android se escriben aquí,
      enteros y juntos, y se revisan antes de seguir.
      - [x] Portado, con 134 pruebas que corren en una décima de segundo.
            De las 124 de iOS se quedan fuera las 3 del nonce de Apple, que
            solo sirve para el inicio de sesión nativo; el resto son de
            Android: el `+` en una consulta, el espacio duro delante de una
            dirección, no partir un emoji al recortar, los `null` del
            servidor, y que ningún texto hable del iPhone.
      - [x] Dos cambios de comportamiento respecto a iOS, y no por gusto:
            el error de inicio de sesión dice el proveedor, porque aquí
            Google y Apple van los dos por web; y `Elemento` esconde su
            `copy`, que en Kotlin dejaría cambiar un campo sin mover
            `actualizadoEn`.
      - [x] `animal-sniffer` con las firmas de la API 28 en `verificar`.
            `dominio` se compila contra el JDK 21 y dos llamadas que no
            existen en Android 9 (`Locale.of` y `URLDecoder.decode` con un
            `Charset`) pasaban la compilación y las pruebas. Lint no las ve
            en un módulo sin Android; se probó antes de descartarlo.
      - [ ] Revisar los textos.
- [ ] **2. Fontanería.** Almacén SQLite con el esquema de Windows,
      comprobado columna por columna en una prueba; cliente HTTP; sesión con
      rotación; sincronizador; `GuardarEnlace`, `CrearEtiqueta` y resolver
      metadatos en el teléfono cuando no hay cuenta. El Keystore, en `app`.
- [ ] **3. Interfaz.** Lista (búsqueda, filtro, acciones y eliminación),
      elegir etiquetas, añadir, detalle, gestionar etiquetas, ajustes,
      bienvenida e inicio de sesión. Cada pantalla, con sus textos revisados
      antes, instalada en el teléfono y escuchada con TalkBack el mismo día.
      Aquí se mide todo lo que la tabla deja «a medir» y se escribe
      `docs/ACCESIBILIDAD.md`.
- [ ] **4. Compartir desde otras apps** y guardado silencioso, con el
      interruptor en Ajustes.
- [ ] **5. Firma de publicación**, el almacén de claves fuera del repositorio
      y dónde se guarda su copia.
- [ ] **6. Repartirla.** Google Play (pruebas internas primero) o el APK
      desde la web, junto al botón de iPhone. Se decide cuando haya app.

Como en iOS, la instalación en el teléfono no espera a la fase 5: desde la
fase 0 se instala con la firma de depuración, y cada pantalla se oye con
TalkBack cuando se termina, no al final.

## Herramientas en este Mac

El 2026-09-23 no había nada para Android: ni JDK (`/usr/bin/java` es el
lanzador de macOS y respondía «Unable to locate a Java Runtime»), ni Android
SDK, ni `adb`, ni Gradle. Se instaló así:

| Qué | Cómo | Cómo se quita |
|---|---|---|
| JDK 21 | `brew install openjdk@21`. Homebrew no lo enlaza al sistema: los scripts lo buscan en `/opt/homebrew/opt/openjdk@21` | `brew uninstall openjdk@21` |
| Android SDK | Las *command-line tools* de Google (suma SHA-1 comprobada contra su repositorio) en `~/Library/Android/sdk/cmdline-tools/latest`, y con `sdkmanager`: `platform-tools` (trae `adb`) y `platforms;android-37.0`. Las `build-tools` las baja Gradle en la versión que pide el plugin. Aceptar las licencias del SDK es obligatorio para descargar nada | Borrar `~/Library/Android/sdk` |
| Gradle | Ninguno instalado. El proyecto lleva su `gradlew`, generado una vez con una copia de Gradle descargada aparte, y con la suma SHA-256 de la distribución fijada en `gradle-wrapper.properties` | — |

Descartado:

- **El instalador de Temurin**: va a `/Library/Java`, para todo el sistema.
- **El paquete de Homebrew `android-commandlinetools`**: deja el SDK en una
  ruta de Homebrew que luego hay que explicarle a Gradle.
- **Un contenedor**: en un Mac con Apple Silicon, `adb` dentro de un
  contenedor no ve el USB, y el teléfono es imprescindible.
- **El emulador**, por ahora: las pruebas corren en la JVM y lo de oído se
  hace en el teléfono. Se deja para cuando haga falta probar otra versión de
  Android.

## Cabos sueltos

- **Qué se lee en cada fila** (opción a o b de la tabla). Es de
  accesibilidad y lo decides tú.
- **La renovación simultánea en iOS.** Si el análisis de «La sesión» es
  correcto, dos 401 a la vez pueden cerrar la sesión del iPhone. No está
  comprobado y no se toca en esta rama, que no toca `app-ios-nativa`.
- **El esquema `guardarenlaces` se puede suplantar en Android** cuando no hay
  Auth Tab. Cerrarlo del todo pide un cambio de contrato (PKCE o App Links).
- **Apple no está configurado en el servidor compartido.** Hasta entonces, el
  botón de Apple no se puede probar.
- **La copia de seguridad incluye la base de datos** y excluye el token. Es
  una decisión que se puede discutir.
- **`targetSdk` 37**: se puede subir cuando se quiera (ver «Decisiones»);
  hay que probar la app en un teléfono con Android 17 antes.
- **Versiones exactas** de Kotlin, del plugin de Android, de Compose y del
  resto: fijadas en `gradle/libs.versions.toml` el 2026-09-23, las últimas
  estables de ese día.
