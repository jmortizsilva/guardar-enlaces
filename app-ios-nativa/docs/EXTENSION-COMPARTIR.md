# La extensión de compartir

Una extensión es **otro programa**. No comparte con la aplicación ni el
sandbox, ni el proceso, ni una sola línea de código que no esté en un paquete.
Casi todo lo raro de esta fase sale de ahí.

## Qué se comparte, y cómo

| Qué | Dónde vive | Lo que hace falta |
|---|---|---|
| Los enlaces | `guardalo.db` en el App Group | `com.apple.security.application-groups` en los dos objetivos |
| La sesión | Llavero | `keychain-access-groups` en los dos, **con el mismo primer valor** |
| El interruptor de guardar sin preguntar | `UserDefaults` del App Group | lo mismo que la base de datos |
| El código | Paquetes `Dominio` y `Fontaneria` | estar en los dos objetivos |

La aplicación de Expo no podía hacer esto: su extensión rehacía por su cuenta
el llavero, la renovación del token y la subida al servidor, 315 líneas que
repetían lo que ya existía en TypeScript. Aquí guardar son tres líneas porque
es exactamente el mismo `GuardarEnlace` que usa la pantalla de añadir.

## 1. El grupo por defecto del llavero no es el mismo en los dos

Sin declarar nada, cada programa guarda en un grupo que sale de su propio
identificador: la aplicación en `…guardarenlaces.nativa` y la extensión en
`…guardarenlaces.nativa.Compartir`. La extensión no vería la sesión.

Se arregla declarando `keychain-access-groups` en ambos con **el mismo primer
valor**, y ese primero tiene que ser el que la aplicación ya usaba
(`$(AppIdentifierPrefix)com.jmortizsilva.guardarenlaces.nativa`): el primero
de la lista es el grupo por defecto, así que lo ya guardado se sigue leyendo y
nadie tiene que volver a entrar.

## 2. Dos procesos en la misma base de datos

Con el diario de siempre, quien escribe bloquea la base entera y el otro
recibe «database is locked» en el acto. Al abrir se piden ahora las dos cosas:

```sql
PRAGMA journal_mode = WAL;    -- uno escribe mientras el otro lee
PRAGMA busy_timeout = 5000;   -- y el choque que queda es cola, no error
```

Con `WAL` hay **tres** ficheros y no uno: `guardalo.db`, `-wal` y `-shm`. La
mudanza a la carpeta compartida se los lleva todos; llevarse solo el primero
perdería lo último guardado.

## 3. Compartir con el teléfono bloqueado

Los ficheros nuevos se crean con la protección por defecto de iOS, que los
deja ilegibles hasta que alguien desbloquea la pantalla. Compartir un enlace
desde la pantalla de bloqueo hacía fallar la apertura de la base, y el enlace
se perdía sin decir nada. La base se marca con
`completeUntilFirstUserAuthentication`, que la deja legible desde el primer
desbloqueo tras encender el teléfono.

**Después de abrirla, no antes**: los ficheros del diario los crea SQLite al
abrir, y hasta entonces no hay nada que marcar.

## 4. Cerrar la hoja mata la extensión

`completeRequest` no es «cerrar la ventana»: el sistema se lleva el proceso
por delante. Subir el enlace al servidor después de esa llamada es no subirlo
nunca. Se sube antes, con un límite de tres segundos, y si no da tiempo queda
encolado como cualquier otro cambio local.

## 5. La carpeta sincronizada mete el `Info.plist` como recurso

Con `PBXFileSystemSynchronizedRootGroup`, todo lo que hay en la carpeta entra
en el objetivo, y el `Info.plist` acaba copiándose además de procesarse:

```
error: Multiple commands produce '…/Compartir.appex/Info.plist'
```

Se excluye con un `PBXFileSystemSynchronizedBuildFileExceptionSet` que liste
`Info.plist` en `membershipExceptions`, que es lo que escribe Xcode cuando lo
hace por su cuenta.

## 6. `containerURL` miente en el Mac

`containerURL(forSecurityApplicationGroupIdentifier:)` devuelve `nil` en iOS
si el grupo no está en los permisos, pero en macOS **devuelve una carpeta para
cualquier nombre, y además la crea**. Una prueba que esperaba el error pasaba
en el simulador y en el Mac dejaba carpetas en `~/Library/Group Containers`.
Ese camino no se puede probar en el Mac.

## Lo que la extensión NO hace

- **No abre la aplicación.** No hay API pública para eso; se hace recorriendo
  la cadena de responders, que Apple ha roto entre versiones. Con hoja propia
  no hace falta.
- **No crea etiquetas.** Se eligen las que haya; crear es de la aplicación.
- **No aparece al compartir texto suelto**, solo direcciones
  (`NSExtensionActivationSupportsWebURLWithMaxCount`). Si alguna aplicación
  comparte el enlace metido en una frase, se saca con
  `Enlaces.direccionDentroDe`.
