# Importar enlaces de otra aplicación

Fuente de la verdad del importador para los **tres clientes** (`app-windows/`,
`app-ios-nativa/`, `app-android/`), igual que `backend/docs/CONTRATO-API.md`
lo es del API. Cualquier cambio de comportamiento se decide aquí primero.

El backend no se entera de nada de esto: importar es crear enlaces locales, y
de subirlos ya se encarga la sincronización de siempre, en lotes de hasta 1000
por llamada. Ni ruta nueva, ni cambio de contrato.

**Se escribe tres veces y tiene que comportarse igual.** Para eso están
`pruebas-compartidas/importacion/`: cuatro ficheros de ejemplo con los casos
difíciles, y un `esperado.json` con lo que debe salir de cada uno. Las pruebas
de los tres clientes leen esos mismos ficheros. Si uno se desvía, su prueba
falla; sin esto, la diferencia no aparece hasta que alguien importa.

## Formatos

| Formato | De dónde sale | Qué trae |
|---|---|---|
| HTML de marcadores | Chrome, Firefox, Safari, Edge, Raindrop | dirección, título, **carpeta**, fecha |
| CSV | Pocket, Instapaper, Raindrop | dirección, título, etiquetas, fecha, a veces carpeta |
| TXT | cualquier sitio, o a mano | solo direcciones |

Markdown no tiene formato propio: es texto con enlaces dentro, así que entra
por el camino de TXT. OPML queda fuera a propósito — es para suscripciones de
RSS, no para enlaces guardados, y sería código que mantener para un fichero
que no va a aparecer.

**El formato se reconoce por el contenido, no por la extensión.** Un fichero
que empieza por `<!DOCTYPE NETSCAPE-Bookmark-file-1>` es de marcadores aunque
se llame `.txt`, y quien exporta no siempre conserva la extensión.

## HTML de marcadores

- Cada `<A HREF="…">` es un enlace; el texto de dentro es el título.
- **Las entidades se decodifican**: `&amp;` es `&`. Si no, los títulos llegan
  con `&amp;` a la vista.
- `ADD_DATE` viene **en segundos** y aquí todo va en milisegundos.
- La **carpeta que contiene el enlace** se convierte en etiqueta. Si están
  anidadas, manda la de dentro: un marcador en «Recetas/Postres» llega con
  «Postres», que es la que dice algo.
- **La barra de marcadores no cuenta como carpeta.** Es dónde vive casi todo
  y no significa nada; una etiqueta «Barra de marcadores» en cientos de
  enlaces es ruido. Se reconoce por `PERSONAL_TOOLBAR_FOLDER="true"`, y
  también se ignora la raíz (`Bookmarks`, `Marcadores`).

## CSV

No hay un CSV de marcadores estándar: cada aplicación pone las columnas que
quiere. **Se miran los nombres de la cabecera**, no su posición:

| Se busca | Nombres aceptados |
|---|---|
| Dirección | `url`, `URL`, `link` |
| Título | `title`, `titulo`, `name` |
| Etiquetas | `tags`, `etiquetas`, `labels` |
| Carpeta | `folder`, `carpeta` |
| Fecha | `time_added`, `created`, `date`, `creado` |

Sin columna de dirección no hay nada que importar, y se dice así.

- **El separador se deduce de la cabecera**: coma, punto y coma o tabulador,
  el que haga aparecer la columna de direcciones. Excel en español separa por
  punto y coma, porque la coma es su separador decimal. La primera versión de
  Windows lo detectaba como CSV pero lo leía con comas, y decía «ese fichero
  no tiene ninguna dirección»: está en `excel.csv` para que no vuelva.
- Un separador dentro de un campo entre comillas es texto, no separa nada.

- Las etiquetas vienen separadas por `|` (Pocket) o por `,` (Raindrop). Se
  aceptan las dos.
- Si hay carpeta **y** etiquetas, la carpeta va primero y las etiquetas
  detrás.
- La fecha puede venir en segundos (Pocket) o en ISO 8601 (Raindrop).

## TXT

Una dirección por línea. Se aprovecha lo que ya hay para compartir desde otras
aplicaciones: de cada línea se saca la primera dirección que contenga, así que
«Mira esto: https://…» también entra. Las líneas sin ninguna dirección se
ignoran sin más, incluidas las de comentario.

## Reglas que valen para los tres formatos

1. **Solo `http://` y `https://`.** Fuera quedan los `javascript:` de los
   bookmarklets y los `place:` internos de Firefox, que si no acaban en la
   lista como enlaces que no abren nada.
2. **Repetidos dentro del propio fichero: se queda el primero.**
3. **Repetidos con lo que ya tienes guardado: se saltan.** El tuyo no se toca.
   Actualizar cientos de golpe pisaría títulos y etiquetas puestos a mano, y
   eso no se puede deshacer. Es a propósito distinto de guardar un enlace
   repetido a mano, que sí actualiza: ahí lo estás haciendo de uno en uno y
   viéndolo.
4. **Etiquetas repetidas: se quedan una vez.** Al comparar no cuentan
   mayúsculas ni tildes, así que «Postres» y «postres» son la misma y se
   conserva la primera que apareció.
5. **Sin título no se inventa nada.** El enlace se guarda sin él; la lista ya
   sabe enseñar el dominio cuando falta.
6. **Sin fecha, la de la importación.**
7. **No se piden metadatos al servidor.** El fichero ya trae el título, y
   salir a buscar ochocientas páginas sería lento y un abuso.

## Lo que se ve y se oye

Los textos concretos van en cada cliente, pero el guion es el mismo:

- Se elige el fichero con el selector del sistema. Dónde está la opción:
  en Windows, **Archivo → Importar enlaces**; en el iPhone, **Ajustes →
  Importar enlaces**, en su propia sección. Es algo que se hace una vez, y no
  merece sitio en la lista.
- **No hace falta avisar del progreso.** Se midió antes de escribir la
  pantalla: 5000 marcadores tardan 175 ms en leerse y prepararse, y guardarlos
  otros pocos. Una barra de progreso para eso solo añade una ventana que
  cerrar y una frase que oír.
- **Al terminar se dice el resultado, no que la acción ocurrió**: «Importados
  143 enlaces. 12 ya los tenías.», no «Importación completada».
- **Y se dice en un cuadro con un botón Aceptar**, no solo en voz alta.
  Salió al probarlo en Windows: una frase que solo se oye se pierde, no hay
  dónde volver a leerla y quien no usa lector de pantalla no se entera de
  nada.
- **El texto va en un campo de solo lectura que se puede recorrer**, y no
  en el mensaje suelto de un cuadro del sistema: ese se lee entero al abrirse,
  pero no deja volver a una cifra con las flechas. El campo se llama
  «Resultado», tiene el foco al abrirse y el cursor al principio. En el iPhone
  y en Android hay que buscar el equivalente — un texto que VoiceOver y
  TalkBack dejen recorrer por palabras o caracteres —, no una alerta simple
  sin más.
- **Sin anuncio además del cuadro.** El lector ya lee el cuadro al abrirse;
  con las dos cosas, la misma frase suena dos veces seguidas.
- Los errores (no se pudo abrir el archivo) van por el mismo camino, en el
  mismo cuadro.
- Si el fichero no tiene ni un enlace, se dice por qué se cree que es: no es
  lo mismo «no se reconoce el formato» que «no hay ninguna dirección dentro».
