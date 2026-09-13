"""En que fila queda la lista despues de rehacerla (logica pura, sin wx)."""

from __future__ import annotations


def fila_tras_refrescar(ids_antes: list[str], seleccionada: int, ids_nuevos: list[str]) -> int:
    """La fila que queda activa, o -1 si la lista queda vacia.

    Nunca -1 habiendo filas: al entrar en una lista sin fila activa el lector
    dice su nombre y nada mas, y hay que pulsar una flecha a ciegas.

    Se sigue al mismo enlace aunque cambie de sitio (una sincronizacion puede
    reordenar). Si ya no esta, se queda en la misma posicion, o en la ultima si
    la lista ha encogido.
    """
    if not ids_nuevos:
        return -1
    if not 0 <= seleccionada < len(ids_antes):
        return 0
    id_seleccionado = ids_antes[seleccionada]
    if id_seleccionado in ids_nuevos:
        return ids_nuevos.index(id_seleccionado)
    return min(seleccionada, len(ids_nuevos) - 1)
