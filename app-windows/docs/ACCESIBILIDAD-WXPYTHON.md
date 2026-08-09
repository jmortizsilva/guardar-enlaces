# Hallazgos de accesibilidad — wxPython + NVDA

Sin equivalente en `comun/` (esa carpeta es solo para iOS/React Native, ver
`CLAUDE.md` raíz): este documento recoge lo aprendido en *este* proyecto
probando con NVDA real. Si algún día hay un segundo proyecto Windows en el
repo, esto se traslada a un sitio compartido; hasta entonces se queda aquí.

## 1. El texto de sugerencia de un control NO es su nombre accesible

`wx.SearchCtrl.SetDescriptiveText(...)` (o el `SetHint(...)` de un
`wx.TextCtrl`) pone el texto atenuado que se ve mientras el campo está
vacío, pero **NVDA no lo usa como nombre del control** — lo anuncia como
"cuadro de edición" sin más. Comprobado a mano con NVDA real.

**Arreglo**: poner un `wx.StaticText` con la etiqueta justo delante del
control, en el mismo orden de creación/tabulación (es como los diálogos
nativos de Win32 llevan haciendo la asociación automática "toda la vida":
por adyacencia en el orden de tabulación, no por ningún atributo especial).
No hace falta ninguna API de asociación explícita — basta el orden.

```python
etiqueta = wx.StaticText(panel, label="&Buscar:")
sizer.Add(etiqueta, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 4)
campo = wx.SearchCtrl(panel)   # inmediatamente despues, mismo sizer
sizer.Add(campo, 1, wx.ALIGN_CENTER_VERTICAL)
```

Se puede seguir usando `SetDescriptiveText`/`SetHint` como ayuda visual
adicional (ejemplo de formato), pero nunca como sustituto de la etiqueta.

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
