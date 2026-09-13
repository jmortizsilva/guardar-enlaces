# Hallazgos de accesibilidad — wxPython + NVDA

Sin equivalente en `comun/` (esa carpeta es solo para iOS/React Native, ver
`CLAUDE.md` raíz): este documento recoge lo aprendido en *este* proyecto sobre
cómo llega la interfaz al lector de pantalla. Comprobado con wxPython 4.3.1 en
Windows 10.

Lo que se puede comprobar sin lector está en `tests/test_interfaz.py`. El
resto hay que probarlo con NVDA.

## 1. El nombre de un campo sale del texto estático creado justo antes

> **Corregido el 13/09/2026.** Este apartado decía lo contrario: que un
> `wx.StaticText` al lado no daba nombre y que hacía falta `SetName()`. Era
> falso. Con ese arreglo, el buscador y la lista se anunciaban sin nombre, y la
> prueba que miraba `GetName()` pasaba en verde.

Windows toma como nombre de un control el `wx.StaticText` **creado
inmediatamente antes** que él, con el mismo padre. Cuenta el orden de creación:
ni el sizer ni la posición en pantalla cambian nada.

`SetName()` no sirve. Es el nombre interno de wx y no llega al lector.
Comprobado leyendo MSAA de la aplicación en marcha: los controles con
`SetName()` no tenían nombre, y el desplegable de etiquetas, que sí tenía su
`StaticText` delante, se anunciaba como «Etiqueta:».

`ui/campos.py` tiene `con_etiqueta`, que crea los dos en ese orden. Recibe una
función que construye el control, y no el control ya hecho, para que el orden no
dependa de acordarse.

```python
etiqueta, campo = con_etiqueta(panel, "&Buscar:", wx.TextCtrl)
sizer.Add(etiqueta, ...)
sizer.Add(campo, ...)
```

La letra marcada con `&` lleva el foco al campo con Alt. Dos controles de la
misma pantalla no pueden tener la misma letra (Windows alterna entre ellos), y
no puede ser una letra con tilde. Tampoco puede coincidir con la de un menú de
la barra: Alt+A abre Archivo.

### `wx.SearchCtrl` rompe la regla

En Windows es una ventana que contiene un campo de texto y dos botones. El foco
va al campo de dentro, y ese campo no tiene ningún `StaticText` delante: se
anunciaba como «edición» a secas. En su lugar va un `wx.TextCtrl` normal.

## 2. El texto que hay que leer va en un cuadro de sólo lectura

A un `wx.StaticText` no llega el tabulador ni se puede recorrer con las
flechas. Lo que hay que poder leer —instrucciones, vista previa, datos de un
enlace, un mensaje de estado— va en un `wx.TextCtrl` con `TE_READONLY` y con su
etiqueta delante.

**Siempre multilínea** (`ESTILO_SOLO_LECTURA`): de una sola línea, el tabulador
lo salta. Y `AcceptsFocus()` devuelve `True` igualmente, así que por ahí no se
detecta.

**Oculto mientras esté vacío**, junto con su etiqueta (`mostrar_con_etiqueta`).
Era el fallo del cuadro de estado del inicio de sesión: estaba vacío, era el
primer control de la pantalla y las flechas no leían nada.

Y si aparece un aviso como respuesta a un botón, el foco va al cuadro del aviso.
Con el foco en el botón, el texto cambia y nadie lo oye.

## 3. Acciones sobre un elemento: menú contextual, no solo botones sueltos

Para exponer varias acciones sobre el elemento seleccionado de una lista
(abrir, copiar URL, editar etiquetas, eliminar) sin depender del ratón,
`wx.EVT_CONTEXT_MENU` es el evento correcto: lo dispara tanto el clic
derecho como la tecla "Menú"/"Aplicaciones" del teclado y Mayús+F10 — así
un usuario de NVDA/JAWS lo abre exactamente igual que en cualquier app
nativa de Windows, sin tener que memorizar atajos propios de la app.

Al abrirse por teclado, `evento.GetPosition()` viene como
`wx.DefaultPosition`: hay que actuar sobre el elemento con el foco actual
(`wx.ListCtrl.GetFirstSelected()`), no sobre una posición de pantalla.

## 4. Lista: `wx.ListCtrl` en modo `report`, siempre con una fila activa

Envuelve el control nativo Win32 `SysListView32`; NVDA/JAWS/Narrador lo
anuncian de forma consistente (número de elementos, texto, selección). La
columna 0 lleva todo el texto legible de la fila (ver `presentacion.py`)
para que baste con que se anuncie esa columna. Va sin cabecera
(`LC_NO_HEADER`): con una columna no añade nada a lo que dice la etiqueta.

**Una lista con filas y sin fila activa no le da nada que leer al lector**: al
entrar se oye su nombre y nada más. `DeleteAllItems` quita la selección, así
que después de rehacerla hay que marcar una fila con `Select` **y** `Focus`.
`Focus` no es el foco del teclado: es la fila activa, la que lee el lector al
llegar. La fila la decide `seleccion.py`: la del mismo enlace si sigue, y si
no, la misma posición.

Si no ha cambiado nada, no se rehace. Se llama en cada sincronización, también
mientras alguien está recorriendo la lista.

Pendiente de contrastar con `wx.dataview.DataViewListCtrl` si algún día hace
falta una lista virtual muy grande — no ha hecho falta todavía.

## 5. El foco inicial se pone con la ventana ya activa

Ni en el constructor ni justo después de `Show()`: la ventana aún no está
activa, y al activarse el foco va al primer control. Comprobado leyendo el foco
de la aplicación en marcha: llamando a `enfocar_lo_primero()` después de
`Show()`, el foco seguía en el buscador.

La ventana principal hace `Show()`, `Raise()` y
`wx.CallAfter(ventana.enfocar_lo_primero)` desde `main.py`. El diálogo de
inicio de sesión pone el foco en el botón con un `wx.CallAfter` desde
`ShowModal`.

## 6. Avisos y preguntas: nada de OK ni de Sí y No

`wx.MessageBox` sale con el botón «OK» en inglés y no deja cambiarlo. Los
avisos van por `preguntas.avisar`, un `wx.MessageDialog` con
`SetOKLabel("&Aceptar")`. Una prueba impide volver a usar `wx.MessageBox`.

Un cuadro de Sí y No no tiene botón de cancelar, y sin él Escape no lo cierra.
Las confirmaciones usan `wx.OK | wx.CANCEL` con `SetOKCancelLabels`, y el botón
dice la acción («Eliminar», «Cerrar sesión»). Donde la acción no se puede
deshacer, Cancelar es el botón de partida (`wx.CANCEL_DEFAULT`).

Un diálogo propio sin botón `wx.ID_CANCEL` necesita `SetEscapeId` para que
Escape sepa qué botón pulsar.

Esto sale de cómo documenta Windows sus cuadros de mensaje; falta comprobarlo
con teclado en la aplicación.

## 7. La barra de estado no se lee sola

Con NVDA, la barra de estado no se oye cuando cambia; solo al pedirla con
NVDA+Fin. Lo que se escribe ahí como respuesta a algo (añadido, eliminado, URL
copiada, un fallo al sincronizar) se dice además con `voz.py`, que habla con el
lector a través de prism (paquete `prismatoid`). Comprobado con prismatoid
0.18.2:

- **Solo lectores de pantalla.** Sin lector abierto, prism elige una voz del
  sistema (OneCore o SAPI) y la aplicación hablaría en voz alta a quien la usa
  sin lector. Esas se descartan.
- **El Narrador se queda sin voz.** La vía de UIA que declara prism falla al
  pedirla (`PrismInvalidParamError`).
- **El lector se busca al abrir la aplicación.** Si se abre después, no habla
  hasta reiniciarla.
- **El número de elementos no se dice**: cambia con cada letra del buscador, y
  la lista ya dice la posición y el total al entrar en ella.
- **Tras cerrarse un menú o un cuadro, la voz sale 500 ms después que la
  barra** (añadido, eliminado, URL copiada, descargando): el foco vuelve a la
  lista, NVDA la relee y pisaría el aviso. NVDA hace lo mismo con sus propios
  avisos cuando hay cambio de ventana. Lo que no viene de cerrar nada (un fallo
  al sincronizar) sale sin esperar.
