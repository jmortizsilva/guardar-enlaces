"""Mismas reglas que backend/src/metadatos/extraccion.ts y que
Metadatos.swift en app-ios-nativa/Dominio: si cambia el criterio de que es un
video o un articulo, o que titulo gana, los tres ficheros tienen que cambiar
o el mismo enlace saldra distinto segun se guardara con cuenta o sin ella."""

from guardar_enlaces.metadatos import (
    es_url_youtube,
    extraer_metadatos,
    metadatos_desde_oembed,
    url_oembed,
)
from guardar_enlaces.resolver_metadatos import (
    CABECERAS,
    _sesion_http,
    resolver_en_este_equipo,
)


def _pagina(cabeza: str) -> str:
    return f"<html><head>{cabeza}</head><body>lo de menos</body></html>"


class TestTitulo:
    def test_open_graph_gana_al_titulo_de_la_pestana(self):
        html = _pagina(
            '<title>Lo que pone la pestana</title>'
            '<meta property="og:title" content="Lo que pone Open Graph">'
        )
        assert extraer_metadatos(html)["titulo"] == "Lo que pone Open Graph"

    def test_sin_open_graph_vale_el_titulo_de_la_pestana(self):
        html = _pagina("<title>Lo que pone la pestana</title>")
        assert extraer_metadatos(html)["titulo"] == "Lo que pone la pestana"

    def test_un_titulo_vacio_es_como_no_tenerlo(self):
        assert extraer_metadatos(_pagina("<title>   </title>"))["titulo"] is None

    def test_sin_nada_que_leer_no_hay_titulo(self):
        assert extraer_metadatos("<html></html>")["titulo"] is None

    def test_el_orden_de_property_y_content_da_igual(self):
        html = _pagina('<meta content="Al reves" property="og:title">')
        assert extraer_metadatos(html)["titulo"] == "Al reves"

    def test_la_etiqueta_puede_llevar_atributos_de_mas(self):
        html = _pagina('<title lang="es" dir="ltr">Con atributos</title>')
        assert extraer_metadatos(html)["titulo"] == "Con atributos"


class TestEntidades:
    def test_se_traducen_las_cinco_del_backend(self):
        html = _pagina(
            '<meta property="og:title" content="&amp; &lt; &gt; &quot; &#39;">'
        )
        assert extraer_metadatos(html)["titulo"] == "& < > \" '"

    def test_las_demas_se_quedan_sin_traducir(self):
        """A proposito: arreglarlo solo aqui es lo que haria que el mismo
        enlace se viera distinto segun quien resolvio sus metadatos."""
        html = _pagina('<meta property="og:title" content="Caf&eacute;">')
        assert extraer_metadatos(html)["titulo"] == "Caf&eacute;"


class TestTipo:
    def test_video(self):
        html = _pagina('<meta property="og:type" content="video.movie">')
        assert extraer_metadatos(html)["tipo"] == "video"

    def test_articulo(self):
        html = _pagina('<meta property="og:type" content="article">')
        assert extraer_metadatos(html)["tipo"] == "articulo"

    def test_imagen_por_image_y_por_photo(self):
        for valor in ("image", "photo"):
            html = _pagina(f'<meta property="og:type" content="{valor}">')
            assert extraer_metadatos(html)["tipo"] == "imagen"

    def test_lo_que_no_se_reconoce_es_un_enlace(self):
        html = _pagina('<meta property="og:type" content="website">')
        assert extraer_metadatos(html)["tipo"] == "enlace"

    def test_sin_og_type_es_un_enlace(self):
        assert extraer_metadatos(_pagina(""))["tipo"] == "enlace"


class TestDescripcionEImagen:
    def test_se_leen_las_dos(self):
        html = _pagina(
            '<meta property="og:description" content="De que va">'
            '<meta property="og:image" content="https://ejemplo.com/foto.jpg">'
        )
        metadatos = extraer_metadatos(html)
        assert metadatos["descripcion"] == "De que va"
        assert metadatos["imagenUrl"] == "https://ejemplo.com/foto.jpg"

    def test_la_clave_de_la_imagen_va_en_camelCase_como_el_contrato(self):
        """dialogo_anadir lee metadatos.get("imagenUrl"): si esta clave se
        renombra, el enlace se guarda sin imagen y sin avisar."""
        assert "imagenUrl" in extraer_metadatos("<html></html>")


class TestYoutube:
    def test_reconoce_sus_cuatro_dominios(self):
        for dominio in ("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"):
            assert es_url_youtube(f"https://{dominio}/watch?v=abc")

    def test_no_se_confunde_con_otros_sitios(self):
        assert not es_url_youtube("https://ejemplo.com/youtube.com")
        assert not es_url_youtube("https://noesyoutube.com/v")

    def test_una_url_que_no_lo_es_no_revienta(self):
        assert not es_url_youtube("esto no es una url")
        assert not es_url_youtube("")

    def test_la_url_del_video_se_escapa_entera(self):
        direccion = url_oembed("https://youtu.be/abc?t=30")
        assert "https%3A%2F%2Fyoutu.be%2Fabc%3Ft%3D30" in direccion
        assert direccion.endswith("&format=json")

    def test_el_oembed_da_titulo_miniatura_y_tipo_video(self):
        cuerpo = b'{"title": "Un video", "thumbnail_url": "https://i.ytimg.com/a.jpg"}'
        assert metadatos_desde_oembed(cuerpo) == {
            "titulo": "Un video",
            "descripcion": None,
            "imagenUrl": "https://i.ytimg.com/a.jpg",
            "tipo": "video",
        }

    def test_si_la_respuesta_no_se_entiende_no_hay_metadatos(self):
        assert metadatos_desde_oembed(b"esto no es json") is None
        assert metadatos_desde_oembed(b"[1, 2, 3]") is None

    def test_un_oembed_sin_los_campos_esperados_no_revienta(self):
        assert metadatos_desde_oembed(b"{}") == {
            "titulo": None,
            "descripcion": None,
            "imagenUrl": None,
            "tipo": "video",
        }


class TestResolverEnEsteEquipo:
    """La descarga se inyecta, asi que estas pruebas no tocan la red."""

    def test_una_pagina_normal_se_raspa(self):
        def bajar(url):
            assert url == "https://ejemplo.com/a"
            return _pagina('<meta property="og:title" content="Hola">').encode()

        assert resolver_en_este_equipo("https://ejemplo.com/a", bajar)["titulo"] == "Hola"

    def test_youtube_se_pregunta_primero_al_oembed(self):
        pedidas = []

        def bajar(url):
            pedidas.append(url)
            return b'{"title": "Un video"}'

        metadatos = resolver_en_este_equipo("https://youtu.be/abc", bajar)
        assert metadatos["titulo"] == "Un video"
        assert metadatos["tipo"] == "video"
        # Una sola peticion: no llego a descargar la pagina.
        assert len(pedidas) == 1
        assert pedidas[0].startswith("https://www.youtube.com/oembed?")

    def test_si_el_oembed_falla_se_cae_al_raspado_de_la_pagina(self):
        def bajar(url):
            if "oembed" in url:
                return None
            return _pagina("<title>Desde la pagina</title>").encode()

        metadatos = resolver_en_este_equipo("https://youtu.be/abc", bajar)
        assert metadatos["titulo"] == "Desde la pagina"

    def test_si_el_oembed_responde_algo_ilegible_tambien_se_cae_al_raspado(self):
        def bajar(url):
            if "oembed" in url:
                return b"<html>un muro de inicio de sesion</html>"
            return _pagina("<title>Desde la pagina</title>").encode()

        assert (
            resolver_en_este_equipo("https://youtu.be/abc", bajar)["titulo"]
            == "Desde la pagina"
        )

    def test_si_no_se_puede_descargar_se_devuelve_vacio(self):
        """Vacio, no una excepcion: guardar el enlace no depende de esto."""
        assert resolver_en_este_equipo("https://ejemplo.com/a", lambda url: None) == {}

    def test_solo_se_descargan_http_y_https(self):
        def bajar(url):
            raise AssertionError("no deberia haberse intentado descargar")

        assert resolver_en_este_equipo("file:///C:/Windows/win.ini", bajar) == {}
        assert resolver_en_este_equipo("javascript:alert(1)", bajar) == {}

    def test_un_html_con_bytes_raros_no_revienta(self):
        def bajar(url):
            return b"<html><title>Caf\xe9 roto</title></html>"

        assert resolver_en_este_equipo("https://ejemplo.com/a", bajar)["titulo"]


class TestComoNosPresentamos:
    """Comprobado contra velocidadcuchara.com, que respondia 403 al
    "python-requests/2.x" que manda requests por su cuenta y 200 en cuanto nos
    presentamos como el navegador que a efectos de esta peticion somos. El
    enlace se guardaba sin titulo y sin decir por que."""

    def test_no_salimos_como_python_requests(self):
        assert "python-requests" not in CABECERAS["User-Agent"]
        assert "requests" not in CABECERAS["User-Agent"].lower()

    def test_la_cabecera_llega_a_la_peticion(self):
        """La constante no sirve de nada si no se aplica a la sesion."""
        assert _sesion_http().headers["User-Agent"] == CABECERAS["User-Agent"]
