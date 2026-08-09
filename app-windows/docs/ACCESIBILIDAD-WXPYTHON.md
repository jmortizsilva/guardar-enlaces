# Hallazgos de accesibilidad — wxPython + NVDA

Sin equivalente en `comun/` (esa carpeta es solo para iOS/React Native, ver
`CLAUDE.md` raíz): este documento recoge lo aprendido en *este* proyecto
probando con NVDA real. Si algún día hay un segundo proyecto Windows en el
repo, esto se traslada a un sitio compartido; hasta entonces se queda aquí.

## 1. Ni el texto de sugerencia ni un StaticText de al lado dan nombre accesible: hace falta `SetName()`

**Primer intento (incorrecto, no lo repitas):** se probó a poner un
`wx.StaticText` justo delante del control, asumiendo que Win32 asocia por
adyacencia/orden de tabulación como en un diálogo nativo clásico. Probado
con NVDA real: **no funciona**, el campo se sigue anunciando como "edición"
a secas. Tampoco basta `SetDescriptiveText`/`SetHint` (el texto atenuado
que se ve mientras el campo está vacío).

**Causa real**: desde wxPython 4.0.4, el soporte de accesibilidad
(`wx.Accessible`) que expone los controles a MSAA/NVDA **exige que cada
control tenga su `Name` fijado explícitamente** — si no, el control se
anuncia con una etiqueta genérica sin más, sin buscar ningún `StaticText`
cercano. Confirmado en
[wxWidgets/Phoenix#1129](https://github.com/wxWidgets/Phoenix/issues/1129).

**Arreglo real**: `control.SetName("texto")` (o el kwarg `name=` del
constructor) en TODO control que necesite un nombre y no sea su propio
texto visible (`wx.TextCtrl`, `wx.SearchCtrl`, `wx.Choice`...). Los
botones normales no lo necesitan: su propio `label` visible ya hace de
nombre accesible.

```python
etiqueta = wx.StaticText(panel, label="&Buscar:")   # solo para quien VE la pantalla
sizer.Add(etiqueta, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 4)

campo = wx.SearchCtrl(panel)
campo.SetName("Buscar por título, URL o etiqueta")   # esto es lo que lee NVDA
sizer.Add(campo, 1, wx.ALIGN_CENTER_VERTICAL)
```

El `wx.StaticText` visual y el `SetName()` son dos cosas independientes:
hacen falta los dos (uno para quien ve, otro para quien no), y ninguno
sustituye al otro.

## 2. Acciones sobre un elemento: menú contextual, no solo botones sueltos

Para exponer varias acciones sobre el elemento seleccionado de una lista
(abrir, copiar URL, editar etiquetas, eliminar) sin depender del ratón,
`wx.EVT_CONTEXT_MENU` es el evento correcto: lo dispara tanto el clic
derecho como la tecla "Menú"/"Aplicaciones" del teclado y Mayús+F10 — así
un usuario de NVDA/JAWS lo abre exactamente igual que en cualquier app
nativa de Windows, sin tener que memorizar atajos propios de la app.

Al abrirse por teclado, `evento.GetPosition()` viene como
`wx.DefaultPosition`: hay que actuar sobre el elemento con el foco actual
(`wx.ListCtrl.GetFirstSelected()`), no sobre una posición de pantalla.

## 3. Control de lista: `wx.ListCtrl` en modo `report`

Envuelve el control nativo Win32 `SysListView32`; NVDA/JAWS/Narrador lo
anuncian de forma consistente (número de elementos, texto, selección). La
columna 0 lleva todo el texto legible de la fila (ver `presentacion.py`)
para que baste con que se anuncie esa columna. Pendiente de contrastar con
`wx.dataview.DataViewListCtrl` si algún día hace falta una lista virtual
muy grande — no ha hecho falta todavía.
