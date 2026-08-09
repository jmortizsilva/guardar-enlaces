"""Orquesta un ciclo de sincronizacion completo: sube lo pendiente, luego
baja lo nuevo del servidor. La decision de como fusionar vive en modelo.py;
aqui solo se encadenan almacen + api + sesion."""

from __future__ import annotations

from .almacen_local import AlmacenLocal
from .api_cliente import ClienteApi
from .modelo import Elemento, aplicar_pull, aplicar_respuesta_push
from .sesion import Sesion


class Sincronizador:
    def __init__(self, almacen: AlmacenLocal, cliente: ClienteApi, sesion: Sesion):
        self._almacen = almacen
        self._cliente = cliente
        self._sesion = sesion

    def sincronizar(self) -> None:
        self._subir_pendientes()
        self._bajar_cambios()

    def _subir_pendientes(self) -> None:
        pendientes = self._almacen.cargar_pendientes()
        if not pendientes:
            return
        lote = [e.to_json_dict() for e in pendientes.values()]
        respuesta = self._sesion.con_reintento(lambda token: self._cliente.push(lote, token))
        definitivos = [Elemento.from_json_dict(d) for d in respuesta["elementos"]]

        cache = aplicar_respuesta_push(self._almacen.cargar_todos(), definitivos)
        self._almacen.guardar(cache)
        self._almacen.limpiar_pendientes([e.id for e in definitivos])

    def _bajar_cambios(self) -> None:
        # Repite el pull mientras el servidor diga que hay mas paginas (biblioteca grande o
        # primera sincronizacion); ver "masDisponible" en docs/CONTRATO-API.md.
        while True:
            desde = self._almacen.cursor()
            respuesta = self._sesion.con_reintento(
                lambda token: self._cliente.pull(desde, token)
            )
            recibidos = [Elemento.from_json_dict(d) for d in respuesta["elementos"]]

            cache = aplicar_pull(
                self._almacen.cargar_todos(), recibidos, self._almacen.cargar_pendientes()
            )
            self._almacen.guardar(cache)

            if respuesta.get("masDisponible") and recibidos:
                self._almacen.fijar_cursor(max(e.actualizado_en for e in recibidos))
            else:
                self._almacen.fijar_cursor(respuesta["servidorEn"])
                break
