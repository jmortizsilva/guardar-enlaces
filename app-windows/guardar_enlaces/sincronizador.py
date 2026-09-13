"""Orquesta un ciclo de sincronizacion completo: sube lo pendiente, luego
baja lo nuevo del servidor. La decision de como fusionar vive en modelo.py;
aqui solo se encadenan almacen + api + sesion."""

from __future__ import annotations

from .almacen_local import AlmacenLocal
from .api_cliente import ClienteApi
from .modelo import Elemento, aplicar_pull, aplicar_respuesta_push
from .sesion import Sesion


# Cuanto se espera como minimo entre dos sincronizaciones automaticas. Volver a
# la ventana dispara una, y sin este freno alternar entre dos aplicaciones seria
# una peticion por cada vuelta.
INTERVALO_MINIMO_SINCRONIZACION_S = 30.0


def toca_sincronizar(
    ultima_s: float,
    ahora_s: float,
    intervalo_s: float = INTERVALO_MINIMO_SINCRONIZACION_S,
) -> bool:
    """Si toca sincronizar otra vez, o es demasiado pronto."""
    return ahora_s - ultima_s >= intervalo_s


def aviso_tras_sincronizar(rechazados: int, manual: bool) -> str:
    """Lo que se dice al terminar sin error, o nada.

    La que se pide (F5, menu, bandeja) confirma siempre: sin respuesta no se
    distingue de que la tecla no haya hecho nada. La automatica, que salta al
    volver a la ventana, calla si todo fue bien para no hablar a cada vuelta.
    Los rechazos se dicen siempre.
    """
    if rechazados == 1:
        return "1 cambio no se pudo subir al servidor"
    if rechazados > 1:
        return f"{rechazados} cambios no se pudieron subir al servidor"
    return "Sincronizado" if manual else ""


class Sincronizador:
    def __init__(self, almacen: AlmacenLocal, cliente: ClienteApi, sesion: Sesion):
        self._almacen = almacen
        self._cliente = cliente
        self._sesion = sesion

    def sincronizar(self) -> int:
        """Devuelve cuantos cambios locales rechazo el servidor (normalmente 0)."""
        rechazados = self._subir_pendientes()
        self._bajar_cambios()
        return rechazados

    def _subir_pendientes(self) -> int:
        pendientes = self._almacen.cargar_pendientes()
        if not pendientes:
            return 0
        lote = [e.to_json_dict() for e in pendientes.values()]
        respuesta = self._sesion.con_reintento(lambda token: self._cliente.push(lote, token))
        definitivos = [Elemento.from_json_dict(d) for d in respuesta["elementos"]]

        cache = aplicar_respuesta_push(self._almacen.cargar_todos(), definitivos)
        self._almacen.guardar(cache)

        # Lo rechazado sale del outbox igual que lo aceptado: el servidor no lo va a
        # admitir por mucho que se insista, y dejarlo dentro reenvia el lote entero en
        # cada sincronizacion, para siempre y sin que se note.
        rechazados = respuesta.get("rechazados") or []
        self._almacen.limpiar_pendientes(
            [e.id for e in definitivos] + [r["id"] for r in rechazados]
        )
        return len(rechazados)

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
