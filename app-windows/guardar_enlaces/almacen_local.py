"""Cache local (SQLite) + "outbox" de cambios pendientes de subir.

Fontaneria: aqui se persiste, la decision de como fusionar vive en modelo.py
(logica pura, ya probada). El outbox es sencillo a proposito: solo el
CONJUNTO de ids con un cambio local sin confirmar. El elemento en si ya esta
en la tabla `elementos` (se actualiza ahi mismo al hacer el cambio local, para
que la interfaz lo vea al instante sin esperar a la red).

Pensado para usarse SIEMPRE desde el mismo hilo de fondo (nunca desde el hilo
de la interfaz): sqlite3 no es seguro entre hilos por conexion compartida.
"""

from __future__ import annotations

import json
import sqlite3
import uuid
from pathlib import Path
from typing import Iterable

from .modelo import Elemento, EtiquetaDefinida


class AlmacenLocal:
    def __init__(self, ruta_bd: str | Path):
        ruta_bd = Path(ruta_bd)
        if str(ruta_bd) != ":memory:":
            ruta_bd.parent.mkdir(parents=True, exist_ok=True)
        self._conexion = sqlite3.connect(str(ruta_bd), check_same_thread=False)
        self._conexion.row_factory = sqlite3.Row
        self._crear_esquema()

    def _crear_esquema(self) -> None:
        self._conexion.executescript(
            """
            CREATE TABLE IF NOT EXISTS elementos (
                id TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                titulo TEXT,
                descripcion TEXT,
                imagen_url TEXT,
                tipo TEXT NOT NULL DEFAULT 'enlace',
                etiquetas TEXT NOT NULL DEFAULT '[]',
                creado_en INTEGER NOT NULL,
                actualizado_en INTEGER NOT NULL,
                borrado INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS outbox (
                id TEXT PRIMARY KEY
            );
            CREATE TABLE IF NOT EXISTS etiquetas_definidas (
                id TEXT PRIMARY KEY,
                nombre TEXT NOT NULL,
                creado_en INTEGER NOT NULL,
                actualizado_en INTEGER NOT NULL,
                borrado INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS outbox_etiquetas (
                id TEXT PRIMARY KEY
            );
            CREATE TABLE IF NOT EXISTS estado_sincronizacion (
                clave TEXT PRIMARY KEY,
                valor INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS estado_texto (
                clave TEXT PRIMARY KEY,
                valor TEXT NOT NULL
            );
            """
        )
        self._conexion.commit()

    def cerrar(self) -> None:
        self._conexion.close()

    # --- cursor de sincronizacion (ultimo "servidorEn" recibido) ---

    # --- dueno de la cache (que cuenta, y de que servidor, dejo estos datos) ---

    def vaciar(self) -> None:
        """Borra la cache entera: elementos, etiquetas reservadas, outbox,
        cursor y dueno."""
        self._conexion.executescript(
            """
            DELETE FROM elementos;
            DELETE FROM outbox;
            DELETE FROM etiquetas_definidas;
            DELETE FROM outbox_etiquetas;
            DELETE FROM estado_sincronizacion;
            DELETE FROM estado_texto;
            """
        )
        self._conexion.commit()

    def dueno_actual(self) -> str | None:
        fila = self._conexion.execute(
            "SELECT valor FROM estado_texto WHERE clave = 'dueno'"
        ).fetchone()
        return fila["valor"] if fila else None

    def fijar_dueno(self, valor: str) -> None:
        self._conexion.execute(
            "INSERT INTO estado_texto (clave, valor) VALUES ('dueno', ?) "
            "ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor",
            (valor,),
        )
        self._conexion.commit()

    def contar_elementos(self) -> int:
        """Enlaces visibles (sin lapidas): lo que cuenta para preguntar."""
        fila = self._conexion.execute(
            "SELECT COUNT(*) AS n FROM elementos WHERE borrado = 0"
        ).fetchone()
        return fila["n"] if fila else 0

    def adoptar_con_ids_nuevos(self) -> None:
        """Adopta lo que hay aqui para la cuenta en la que se acaba de entrar:
        les da IDENTIFICADORES NUEVOS y los mete en el outbox para subirlos.

        Los ids nuevos no son un capricho. Si estos enlaces venian de otra
        cuenta, el servidor ya tiene esos mismos ids a nombre de su dueno
        anterior y rechaza el cambio; antes ademas lo hacia en silencio, y se
        quedaban reintentandose para siempre sin subir jamas. Con id nuevo son
        lo que de verdad son: enlaces de esta cuenta, copiados de lo que habia.

        Las lapidas se tiran: son el rastro de un borrado que la OTRA cuenta ya
        conoce, y en esta no significan nada.
        """
        self._conexion.execute("DELETE FROM elementos WHERE borrado = 1")
        # El outbox referencia los ids viejos: se vacia antes de renumerar.
        self._conexion.execute("DELETE FROM outbox")
        ids = [f["id"] for f in self._conexion.execute("SELECT id FROM elementos")]
        for id_viejo in ids:
            self._conexion.execute(
                "UPDATE elementos SET id = ? WHERE id = ?", (str(uuid.uuid4()), id_viejo)
            )
        self._conexion.execute("INSERT INTO outbox (id) SELECT id FROM elementos")

        # Mismo motivo que con los elementos: los ids de etiquetas reservadas
        # de la cuenta anterior chocarian con los del dueno anterior en el
        # servidor.
        self._conexion.execute("DELETE FROM etiquetas_definidas WHERE borrado = 1")
        self._conexion.execute("DELETE FROM outbox_etiquetas")
        ids_etiquetas = [
            f["id"] for f in self._conexion.execute("SELECT id FROM etiquetas_definidas")
        ]
        for id_viejo in ids_etiquetas:
            self._conexion.execute(
                "UPDATE etiquetas_definidas SET id = ? WHERE id = ?", (str(uuid.uuid4()), id_viejo)
            )
        self._conexion.execute(
            "INSERT INTO outbox_etiquetas (id) SELECT id FROM etiquetas_definidas"
        )
        self._conexion.commit()

    # --- cursor de sincronizacion (ultimo "servidorEn" recibido) ---

    def cursor(self, clave: str = "cursor") -> int:
        fila = self._conexion.execute(
            "SELECT valor FROM estado_sincronizacion WHERE clave = ?", (clave,)
        ).fetchone()
        return fila["valor"] if fila else 0

    def fijar_cursor(self, valor: int, clave: str = "cursor") -> None:
        self._conexion.execute(
            "INSERT INTO estado_sincronizacion (clave, valor) VALUES (?, ?) "
            "ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor",
            (clave, valor),
        )
        self._conexion.commit()

    # --- elementos ---

    def cargar_todos(self) -> dict[str, Elemento]:
        filas = self._conexion.execute("SELECT * FROM elementos").fetchall()
        return {f["id"]: _elemento_de_fila(f) for f in filas}

    def cargar_pendientes(self) -> dict[str, Elemento]:
        filas = self._conexion.execute(
            "SELECT e.* FROM elementos e JOIN outbox o ON o.id = e.id"
        ).fetchall()
        return {f["id"]: _elemento_de_fila(f) for f in filas}

    def guardar(self, cache: dict[str, Elemento]) -> None:
        """Persiste el resultado de aplicar_pull/aplicar_respuesta_push (no
        toca el outbox: eso lo decide quien orquesta la sincronizacion)."""
        for elemento in cache.values():
            self._upsert_elemento(elemento)
        self._conexion.commit()

    def marcar_pendiente(self, elemento: Elemento) -> None:
        """Cambio local (alta/edicion/baja): se ve al instante y se encola
        para el proximo push."""
        self._upsert_elemento(elemento)
        self._conexion.execute(
            "INSERT OR IGNORE INTO outbox (id) VALUES (?)", (elemento.id,)
        )
        self._conexion.commit()

    def limpiar_pendientes(self, ids: Iterable[str]) -> None:
        self._conexion.executemany(
            "DELETE FROM outbox WHERE id = ?", [(i,) for i in ids]
        )
        self._conexion.commit()

    def _upsert_elemento(self, elemento: Elemento) -> None:
        self._conexion.execute(
            """
            INSERT INTO elementos
                (id, url, titulo, descripcion, imagen_url, tipo, etiquetas,
                 creado_en, actualizado_en, borrado)
            VALUES (:id, :url, :titulo, :descripcion, :imagen_url, :tipo, :etiquetas,
                    :creado_en, :actualizado_en, :borrado)
            ON CONFLICT(id) DO UPDATE SET
                url = excluded.url, titulo = excluded.titulo,
                descripcion = excluded.descripcion, imagen_url = excluded.imagen_url,
                tipo = excluded.tipo, etiquetas = excluded.etiquetas,
                creado_en = excluded.creado_en, actualizado_en = excluded.actualizado_en,
                borrado = excluded.borrado
            """,
            {
                "id": elemento.id,
                "url": elemento.url,
                "titulo": elemento.titulo,
                "descripcion": elemento.descripcion,
                "imagen_url": elemento.imagen_url,
                "tipo": elemento.tipo,
                "etiquetas": json.dumps(list(elemento.etiquetas)),
                "creado_en": elemento.creado_en,
                "actualizado_en": elemento.actualizado_en,
                "borrado": int(elemento.borrado),
            },
        )

    # --- etiquetas reservadas ---

    def cargar_etiquetas_definidas(self) -> dict[str, EtiquetaDefinida]:
        filas = self._conexion.execute("SELECT * FROM etiquetas_definidas").fetchall()
        return {f["id"]: _etiqueta_de_fila(f) for f in filas}

    def cargar_etiquetas_pendientes(self) -> dict[str, EtiquetaDefinida]:
        filas = self._conexion.execute(
            "SELECT e.* FROM etiquetas_definidas e JOIN outbox_etiquetas o ON o.id = e.id"
        ).fetchall()
        return {f["id"]: _etiqueta_de_fila(f) for f in filas}

    def guardar_etiquetas_definidas(self, cache: dict[str, EtiquetaDefinida]) -> None:
        for etiqueta in cache.values():
            self._upsert_etiqueta_definida(etiqueta)
        self._conexion.commit()

    def marcar_etiqueta_pendiente(self, etiqueta: EtiquetaDefinida) -> None:
        self._upsert_etiqueta_definida(etiqueta)
        self._conexion.execute(
            "INSERT OR IGNORE INTO outbox_etiquetas (id) VALUES (?)", (etiqueta.id,)
        )
        self._conexion.commit()

    def limpiar_etiquetas_pendientes(self, ids: Iterable[str]) -> None:
        self._conexion.executemany(
            "DELETE FROM outbox_etiquetas WHERE id = ?", [(i,) for i in ids]
        )
        self._conexion.commit()

    def _upsert_etiqueta_definida(self, etiqueta: EtiquetaDefinida) -> None:
        self._conexion.execute(
            """
            INSERT INTO etiquetas_definidas (id, nombre, creado_en, actualizado_en, borrado)
            VALUES (:id, :nombre, :creado_en, :actualizado_en, :borrado)
            ON CONFLICT(id) DO UPDATE SET
                nombre = excluded.nombre, creado_en = excluded.creado_en,
                actualizado_en = excluded.actualizado_en, borrado = excluded.borrado
            """,
            {
                "id": etiqueta.id,
                "nombre": etiqueta.nombre,
                "creado_en": etiqueta.creado_en,
                "actualizado_en": etiqueta.actualizado_en,
                "borrado": int(etiqueta.borrado),
            },
        )


def _elemento_de_fila(fila: sqlite3.Row) -> Elemento:
    return Elemento(
        id=fila["id"],
        url=fila["url"],
        titulo=fila["titulo"],
        descripcion=fila["descripcion"],
        imagen_url=fila["imagen_url"],
        tipo=fila["tipo"],
        etiquetas=tuple(json.loads(fila["etiquetas"])),
        creado_en=fila["creado_en"],
        actualizado_en=fila["actualizado_en"],
        borrado=bool(fila["borrado"]),
    )


def _etiqueta_de_fila(fila: sqlite3.Row) -> EtiquetaDefinida:
    return EtiquetaDefinida(
        id=fila["id"],
        nombre=fila["nombre"],
        creado_en=fila["creado_en"],
        actualizado_en=fila["actualizado_en"],
        borrado=bool(fila["borrado"]),
    )
