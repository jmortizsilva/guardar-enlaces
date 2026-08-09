from guardar_enlaces.modelo import (
    Elemento,
    aplicar_pull,
    aplicar_respuesta_push,
    buscar,
    editar,
    elementos_visibles,
    etiquetas_disponibles,
    filtrar_por_etiqueta,
    marcar_borrado,
    nuevo_elemento_local,
)


def test_nuevo_elemento_local_genera_id_y_timestamps():
    reloj = lambda: 1000
    e = nuevo_elemento_local("https://a.com", titulo="A", ahora=reloj)
    assert e.url == "https://a.com"
    assert e.titulo == "A"
    assert e.creado_en == 1000
    assert e.actualizado_en == 1000
    assert e.borrado is False
    assert len(e.id) == 36  # uuid4 con guiones


def test_dos_elementos_nuevos_tienen_ids_distintos():
    a = nuevo_elemento_local("https://a.com")
    b = nuevo_elemento_local("https://b.com")
    assert a.id != b.id


def test_marcar_borrado_deja_tombstone_no_elimina():
    e = nuevo_elemento_local("https://a.com", ahora=lambda: 100)
    borrado = marcar_borrado(e, ahora=lambda: 200)
    assert borrado.borrado is True
    assert borrado.actualizado_en == 200
    assert borrado.url == "https://a.com"  # se conserva el resto de datos


def test_editar_cambia_campos_y_timestamp():
    e = nuevo_elemento_local("https://a.com", titulo="Viejo", ahora=lambda: 100)
    editado = editar(e, ahora=lambda: 200, titulo="Nuevo")
    assert editado.titulo == "Nuevo"
    assert editado.actualizado_en == 200
    assert editado.id == e.id


def test_to_json_dict_y_from_json_dict_son_inversas():
    e = nuevo_elemento_local(
        "https://a.com", titulo="A", descripcion="d", imagen_url="https://a.com/i.jpg",
        etiquetas=("ocio", "pendiente"), ahora=lambda: 100,
    )
    reconstruido = Elemento.from_json_dict(e.to_json_dict())
    assert reconstruido == e


def test_from_json_dict_admite_campos_ausentes():
    e = Elemento.from_json_dict({"id": "x1", "actualizadoEn": 5, "borrado": True})
    assert e.url == ""
    assert e.titulo is None
    assert e.etiquetas == ()
    assert e.borrado is True


class TestAplicarPull:
    def test_agrega_elementos_nuevos_a_la_cache(self):
        recibido = nuevo_elemento_local("https://a.com", ahora=lambda: 100)
        cache = aplicar_pull({}, [recibido], pendientes={})
        assert cache[recibido.id] == recibido

    def test_no_pisa_un_cambio_local_mas_reciente_que_el_pull(self):
        base = nuevo_elemento_local("https://a.com", titulo="Original", ahora=lambda: 100)
        pendiente_local = editar(base, ahora=lambda: 300, titulo="Editado en este dispositivo")
        del_servidor = replace_titulo(base, "Version antigua del servidor", actualizado_en=200)

        cache = aplicar_pull(
            {base.id: base}, [del_servidor], pendientes={base.id: pendiente_local}
        )
        assert cache[base.id] == base  # no se toco: el pendiente es mas nuevo que el pull

    def test_si_el_pull_es_mas_nuevo_que_lo_pendiente_si_se_aplica(self):
        base = nuevo_elemento_local("https://a.com", titulo="Original", ahora=lambda: 100)
        pendiente_local = editar(base, ahora=lambda: 150, titulo="Editado en este dispositivo")
        del_servidor = replace_titulo(base, "Ya sincronizado desde otro dispositivo", actualizado_en=500)

        cache = aplicar_pull(
            {base.id: base}, [del_servidor], pendientes={base.id: pendiente_local}
        )
        assert cache[base.id] == del_servidor


def replace_titulo(elemento: Elemento, titulo: str, actualizado_en: int) -> Elemento:
    from dataclasses import replace as dc_replace

    return dc_replace(elemento, titulo=titulo, actualizado_en=actualizado_en)


class TestAplicarRespuestaPush:
    def test_sustituye_por_la_version_definitiva_del_servidor(self):
        local = nuevo_elemento_local("https://a.com", titulo="Mio", ahora=lambda: 100)
        definitivo = replace_titulo(local, "El del servidor gano el conflicto", actualizado_en=50)

        cache = aplicar_respuesta_push({local.id: local}, [definitivo])
        assert cache[local.id] == definitivo


class TestElementosVisibles:
    def test_oculta_los_borrados_y_ordena_mas_reciente_primero(self):
        a = nuevo_elemento_local("https://a.com", ahora=lambda: 100)
        b = nuevo_elemento_local("https://b.com", ahora=lambda: 200)
        c = marcar_borrado(nuevo_elemento_local("https://c.com", ahora=lambda: 300), ahora=lambda: 400)

        visibles = elementos_visibles({a.id: a, b.id: b, c.id: c})
        assert [e.id for e in visibles] == [b.id, a.id]


class TestBuscar:
    def test_filtra_por_titulo_url_o_etiqueta_sin_distinguir_mayusculas(self):
        a = nuevo_elemento_local("https://python.org", titulo="Documentacion Python")
        b = nuevo_elemento_local("https://otra.com", titulo="Otra cosa", etiquetas=("python",))
        c = nuevo_elemento_local("https://nada.com", titulo="Nada que ver")

        assert buscar([a, b, c], "PYTHON") == [a, b]
        assert buscar([a, b, c], "") == [a, b, c]
        assert buscar([a, b, c], "no-existe") == []


class TestEtiquetasDisponibles:
    def test_devuelve_las_etiquetas_distintas_ordenadas(self):
        a = nuevo_elemento_local("https://a.com", etiquetas=("ocio", "pendiente"))
        b = nuevo_elemento_local("https://b.com", etiquetas=("trabajo", "ocio"))
        c = nuevo_elemento_local("https://c.com")  # sin etiquetas

        assert etiquetas_disponibles([a, b, c]) == ["ocio", "pendiente", "trabajo"]

    def test_sin_elementos_o_sin_etiquetas_devuelve_vacio(self):
        assert etiquetas_disponibles([]) == []
        assert etiquetas_disponibles([nuevo_elemento_local("https://a.com")]) == []


class TestFiltrarPorEtiqueta:
    def test_filtra_los_que_tienen_la_etiqueta(self):
        a = nuevo_elemento_local("https://a.com", etiquetas=("ocio",))
        b = nuevo_elemento_local("https://b.com", etiquetas=("trabajo",))

        assert filtrar_por_etiqueta([a, b], "ocio") == [a]

    def test_sin_etiqueta_seleccionada_no_filtra(self):
        a = nuevo_elemento_local("https://a.com", etiquetas=("ocio",))
        b = nuevo_elemento_local("https://b.com")

        assert filtrar_por_etiqueta([a, b], None) == [a, b]
        assert filtrar_por_etiqueta([a, b], "") == [a, b]
