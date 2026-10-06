# Añadir una dirección escrita a mano

Fuente de la verdad para los **tres clientes** (`app-windows/`,
`app-ios-nativa/`, `app-android/`), como `IMPORTAR.md` lo es del importador.
Solo vale para la pantalla de añadir: compartir desde otra aplicación e
importar siguen pidiendo `http://` o `https://`, porque ahí la dirección llega
metida en un texto, y completar cualquier palabra con un punto guardaría cosas
como «p.ej.».

Los casos están en `pruebas-compartidas/direcciones/casos.json`, y las pruebas
de los tres clientes leen ese mismo fichero.

## Completar el esquema

No hace falta escribir `https://`.

- Si lo escrito empieza por `http://` o `https://`, en mayúsculas o en
  minúsculas, se deja **tal cual**, siempre que haya algo detrás.
- Si no, se añade `https://` delante, pero solo si parece una dirección:
  - sin espacios,
  - sin `://` (sería otro esquema, como `ftp://`),
  - y con un punto en el nombre del sitio (lo que hay antes de la primera `/`,
    `?` o `#`) que tenga algo delante y algo detrás.
  - Sin `@` en el nombre del sitio: `pepe@gmail.com` es un correo, y con
    `https://` delante abriría gmail.com.
- Si no cumple nada de eso, no es una dirección.

Cuando el esquema se ha añadido, hay una **alternativa** con `http://`: algunas
páginas antiguas solo responden así. Cuando lo escribió quien usa la app, no
hay alternativa: se respeta lo que puso.

## Comprobar que carga

Antes de guardar se comprueba que la página responde, con la dirección ya
completa.

- **Carga**: se guarda.
- **No carga**: si hay alternativa, se prueba con ella, y si carga se guarda
  esa. Si tampoco, se pregunta: «No se ha podido abrir la página», con
  **Guardar igualmente** y **Cancelar**. No se rechaza sin más porque hay
  páginas que abren en el navegador y rechazan las comprobaciones automáticas,
  sobre todo las que hace el servidor desde un centro de datos.
- **Sin conexión**: no se puede comprobar, y se guarda sin preguntar. La app
  funciona sin red.
