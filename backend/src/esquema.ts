// Esquema de la base de datos (SQLite). Es la FUENTE DE LA VERDAD: el codigo lo aplica al
// arrancar. Base de datos propia y separada de la de servidor-notificaciones (proceso y
// contenedor distintos): nada de esto se mezcla con las tablas de aquel servidor.

export const SCHEMA_SQL = `
CREATE TABLE IF NOT EXISTS usuarios (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  proveedor TEXT NOT NULL,
  id_proveedor TEXT NOT NULL,
  email TEXT NOT NULL,
  creado_en INTEGER NOT NULL,
  activo INTEGER NOT NULL DEFAULT 1,
  UNIQUE (proveedor, id_proveedor)
);

CREATE TABLE IF NOT EXISTS login_pendientes (
  estado TEXT PRIMARY KEY,
  modo TEXT NOT NULL,
  esquema TEXT,
  creado_en INTEGER NOT NULL,
  expira_en INTEGER NOT NULL,
  codigo_canje TEXT,
  usuario_id INTEGER REFERENCES usuarios(id),
  error TEXT
);

CREATE INDEX IF NOT EXISTS idx_login_pendientes_codigo_canje
  ON login_pendientes (codigo_canje);

CREATE TABLE IF NOT EXISTS sesiones (
  id TEXT PRIMARY KEY,
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  hash_token_refresco TEXT NOT NULL,
  dispositivo TEXT,
  creado_en INTEGER NOT NULL,
  ultimo_uso_en INTEGER,
  expira_en INTEGER NOT NULL,
  revocado_en INTEGER
);

CREATE TABLE IF NOT EXISTS elementos (
  id TEXT PRIMARY KEY,
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  url TEXT NOT NULL,
  titulo TEXT,
  descripcion TEXT,
  imagen_url TEXT,
  tipo TEXT NOT NULL DEFAULT 'enlace',
  etiquetas TEXT,
  creado_en INTEGER NOT NULL,
  actualizado_en INTEGER NOT NULL,
  borrado_en INTEGER
);

CREATE INDEX IF NOT EXISTS idx_elementos_usuario_actualizado
  ON elementos (usuario_id, actualizado_en);

CREATE TABLE IF NOT EXISTS etiquetas_definidas (
  id TEXT PRIMARY KEY,
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  nombre TEXT NOT NULL,
  creado_en INTEGER NOT NULL,
  actualizado_en INTEGER NOT NULL,
  borrado_en INTEGER
);

CREATE INDEX IF NOT EXISTS idx_etiquetas_definidas_usuario_actualizado
  ON etiquetas_definidas (usuario_id, actualizado_en);

CREATE INDEX IF NOT EXISTS idx_sesiones_usuario
  ON sesiones (usuario_id);
`;
