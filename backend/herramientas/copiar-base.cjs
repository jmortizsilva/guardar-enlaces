// Copia la base de datos del servidor, con lo que haya en el WAL, y la comprueba.
//
// Se lanza desde el servidor, dentro del contenedor (ver docs/DESPLIEGUE.md):
//
//   podman exec -i guardar-enlaces node < ~/guardar-enlaces/backend/herramientas/copiar-base.cjs
//
// Con `cp` no vale. La base va en modo WAL, y lo reciente vive en enlaces.sqlite-wal
// hasta que SQLite lo vuelca. Copiar solo enlaces.sqlite dio el 2026-09-19 una copia de
// 4096 bytes sin ninguna tabla, y no se supo hasta el 30. `backup()` de better-sqlite3
// copia la base entera, WAL incluido.
//
// Cuenta usuarios, enlaces y etiquetas en la base y en la copia: si no coinciden, la
// copia no sirve. Deja la copia en /app/datos/copia.sqlite, que fuera del contenedor es
// ~/podman-volumes/guardar-enlaces/datos/copia.sqlite.

const Database = require('better-sqlite3');
const fs = require('fs');

const datos = '/app/datos';
const base = `${datos}/enlaces.sqlite`;
const destino = `${datos}/copia.sqlite`;

function contar(ruta) {
  try {
    const db = new Database(ruta, { readonly: true });
    const tablas = db
      .prepare("SELECT name FROM sqlite_master WHERE type = 'table'")
      .all()
      .map((t) => t.name);
    const n = (tabla) =>
      tablas.includes(tabla) ? db.prepare(`SELECT COUNT(*) AS n FROM ${tabla}`).get().n : 'sin tabla';
    const resumen = `${n('usuarios')} usuarios, ${n('elementos')} enlaces, ${n('etiquetas_definidas')} etiquetas`;
    db.close();
    return resumen;
  } catch (fallo) {
    return `no se puede leer: ${fallo.message}`;
  }
}

new Database(base, { readonly: true }).backup(destino).then(() => {
  const enLaBase = contar(base);
  const enLaCopia = contar(destino);
  console.log(`base:  ${enLaBase}`);
  console.log(`copia: ${enLaCopia} (${fs.statSync(destino).size} bytes)`);
  console.log(enLaBase === enLaCopia ? 'La copia coincide con la base.' : 'NO COINCIDEN: la copia no sirve.');
});
