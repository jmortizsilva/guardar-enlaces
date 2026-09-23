# Lo que hemos aprendido probando con TalkBack

Comprobado en un teléfono de verdad (Pixel 10a, Android 17, TalkBack 17.0),
no leído en la documentación. Las pruebas del Mac leen el árbol de semántica,
pero no lo oyen: nada de esto lo detectan ellas.

El equivalente de iOS está en
[`../../app-ios-nativa/docs/ACCESIBILIDAD.md`](../../app-ios-nativa/docs/ACCESIBILIDAD.md).
Varias cosas no se parecen.

## Las acciones de la fila salen una vez, y en el orden declarado

Medido el 2026-09-23 con dos acciones en `customActions`: TalkBack las ofrece
en el orden en que se declaran («Copiar URL», «Eliminar»), cada una una sola
vez y sin añadir ninguna suya.

En iOS es al revés: VoiceOver las lee en orden inverso, y hay que declararlas
empezando por el final. Aquí no: se declaran en el orden en que se quieren
oír.

Lo que las duplicaría en Android, y por eso no se usa: la pulsación larga
(`combinedClickable` con `onLongClick`), que añade «mantener pulsado» a las
acciones. Es la versión Android de los gestos de deslizar de iOS.

## Pedir el foco no mueve el cursor de TalkBack

Tras eliminar una fila, `FocusRequester.requestFocus()` sobre la siguiente
dejaba el cursor de TalkBack en el título de la pantalla. La prueba del Mac
pasaba, porque comprueba el foco del teclado, y ese sí se movía.

Son dos focos distintos, y Compose no tiene una forma pública de mover el de
TalkBack. Lo que funciona (2026-09-23) es pedirle al proveedor de
accesibilidad de la vista la acción `ACTION_ACCESSIBILITY_FOCUS` sobre el
nodo: [`CursorDeTalkBack.kt`](../app/src/main/kotlin/com/jmortizsilva/guardarenlaces/CursorDeTalkBack.kt).
Con eso el cursor va a la fila siguiente, o a la anterior si se eliminó la
última, y al cancelar el diálogo vuelve a la misma fila.

Primero se mueve el cursor y después se anuncia el resultado: al revés, el
cambio de cursor corta el anuncio.

**Una vez no siempre basta.** Al eliminar desde el detalle se cierra un
diálogo y se cambia de pantalla a la vez, y TalkBack ponía su cursor en el
buscador *después* de que la app lo pusiera en la fila. Se mueve dos veces,
con medio segundo entre medias. Si la primera ya acertó, la segunda no hace
nada ni se oye nada: Compose no envía el evento si el nodo ya tiene el cursor
(`requestAccessibilityFocus`, visto en Compose 1.12.1).

**Al volver, el cursor va a lo que abrió la pantalla**: a la fila, o en el
detalle, al botón de etiquetas. Y la decisión de llevarlo al título al abrir
una pantalla se toma una sola vez. Se tomaba en cada composición, y al volver
al detalle el cursor llegaba al botón y saltaba enseguida al título.

## El título de la pantalla, una sola vez

Con `paneTitle` en el detalle, Android anunciaba el título al abrirlo, y al
llegar el cursor al encabezado se oía otra vez. En las pantallas a las que se
lleva el cursor al título no se pone `paneTitle`: con el cursor basta, y queda
en la pantalla nueva. La lista lo conserva.

## Un aviso viejo no se repite

Cada pantalla tiene su línea de avisos, y al aparecer volvía a decir el
último aviso que hubiera, de cuando fuera: al volver al detalle se oían las
etiquetas de antes. Lo que ya estaba cuando aparece la línea no se dice.

## La región viva sirve para anunciar

`announceForAccessibility` está obsoleta desde Android 16. Una línea de texto
con `liveRegion = Assertive` al pie de la pantalla se oye entera después de
mover el cursor: «Eliminado, Las mejores alternativas a Pocket»
([`Anuncios.kt`](../app/src/main/kotlin/com/jmortizsilva/guardarenlaces/Anuncios.kt)).
Por ahora no ha hecho falta la API obsoleta.

`Polite` (el `informativo` de iOS) todavía no se ha oído en ningún sitio.

## Android ya avisa al copiar

Desde Android 13 el sistema dice «Texto copiado» al copiar algo. Con nuestro
«URL copiada» se oían los dos seguidos. Solo se dice el nuestro en Android 12
o anterior, que es también lo que recomienda Google.

## Lo que funcionó a la primera

- Dictar en el campo de búsqueda con el micrófono del teclado: sin texto
  duplicado. El campo es el basado en estado (`TextFieldState`), no el de
  `value`/`onValueChange`.
- La opción elegida en el menú del filtro se anuncia como seleccionada
  (`semantics { selected = … }`).
- Tocar dos veces una fila abre la página en una pestaña del navegador, y el
  gesto de atrás de TalkBack vuelve a la lista.
- Las casillas de «Editar etiquetas» se oyen como marcadas o no marcadas
  (`toggleable` con `Role.Checkbox`).
- La búsqueda y el filtro de la lista siguen puestos al volver del detalle.
- El título de la pantalla se anuncia como encabezado.
