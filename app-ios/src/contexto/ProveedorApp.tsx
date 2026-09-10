/**
 * Composition root: cablea sesion + almacen + sincronizador y los expone a
 * las pantallas via tres hooks (useSesion, useElementos, useAcciones).
 * Equivalente TS de AplicacionGuardarEnlaces.OnInit en app-windows/.
 */
import { createContext, ReactNode, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';

import { ClienteApi, RespuestaMetadatos, UsuarioApi } from '../api/clienteApi';
import { AlmacenLocal } from '../almacen/almacenLocal';
import { DecidirImportacion, asentarCuenta, identidadDueno } from '../almacen/asentarCuenta';
import {
  DatosElementoNuevo,
  Elemento,
  editar,
  marcarBorrado,
  nuevoElementoLocal,
} from '../dominio/elemento';
import { elementosVisibles, tocaSincronizar } from '../dominio/sincronizacion';
import { resolverMetadatosEnDispositivo } from '../metadatos/resolverLocal';
import { ResultadoLogin } from '../sesion/loginProveedor';
import { Sesion } from '../sesion/sesion';
import { Sincronizador } from '../sincronizador/sincronizador';

function urlApi(): string {
  const url = process.env.EXPO_PUBLIC_API_URL;
  if (!url) {
    throw new Error(
      'Falta EXPO_PUBLIC_API_URL — copia .env.example a .env y pon la IP del backend',
    );
  }
  return url;
}

interface EstadoApp {
  /** true mientras se intenta restaurar una sesion previa al arrancar. */
  cargando: boolean;
  autenticado: boolean;
  usuario: UsuarioApi | null;
  /**
   * Abre el login de Google. No lanza si el usuario cancela o el proveedor
   * rechaza: eso vuelve en el resultado, para que la pantalla lo anuncie.
   *
   * `decidirImportacion` solo se llama si al entrar hay enlaces en el telefono
   * que no son de esa cuenta (ver asentarCuenta).
   */
  iniciarConGoogle: (decidirImportacion: DecidirImportacion) => Promise<ResultadoLogin>;
  cerrar: () => Promise<void>;

  /** Los no borrados, mas recientes primero (dominio/sincronizacion.elementosVisibles). */
  elementos: Elemento[];
  sincronizando: boolean;
  anadir: (datos: DatosElementoNuevo) => void;
  eliminar: (id: string) => void;
  editarEtiquetas: (id: string, etiquetas: string[]) => void;
  /** Sincroniza ya, en vez de esperar a la siguiente accion. Nunca lanza (ver comentario interno). */
  sincronizar: () => Promise<void>;
  /**
   * Titulo, descripcion e imagen para la vista previa de "Añadir enlace": con
   * cuenta los resuelve el servidor, sin cuenta el propio telefono. Si lanza,
   * es un error real y aqui si se propaga (lo pinta la pantalla).
   */
  comprobarMetadatos: (url: string) => Promise<RespuestaMetadatos>;
}

export type { EnlacesEnElTelefono } from '../almacen/asentarCuenta';

const ContextoApp = createContext<EstadoApp | null>(null);

export function ProveedorApp({ children }: { children: ReactNode }) {
  const cliente = useMemo(() => new ClienteApi(urlApi()), []);
  const sesion = useMemo(() => new Sesion(cliente), [cliente]);
  const almacen = useMemo(() => new AlmacenLocal(), []);
  const sincronizador = useMemo(
    () => new Sincronizador(almacen, cliente, sesion),
    [almacen, cliente, sesion],
  );

  // Para no sincronizar dos veces seguidas al alternar entre aplicaciones.
  const ultimaSincronizacion = useRef(0);
  const [cargando, setCargando] = useState(true);
  const [autenticado, setAutenticado] = useState(false);
  const [usuario, setUsuario] = useState<UsuarioApi | null>(null);
  const [elementos, setElementos] = useState<Elemento[]>([]);
  const [sincronizando, setSincronizando] = useState(false);

  function refrescarDesdeElAlmacen(): void {
    setElementos(elementosVisibles(almacen.cargarTodos()));
  }

  async function sincronizar(): Promise<void> {
    // Modo local: sin cuenta no hay con quien sincronizar, los enlaces viven
    // solo en este telefono. Se sale en silencio porque esto lo llaman cosas
    // que no saben si hay sesion (anadir, borrar, el gesto de refrescar).
    if (!sesion.autenticado) {
      return;
    }
    ultimaSincronizacion.current = Date.now();
    setSincronizando(true);
    try {
      await sincronizador.sincronizar();
      refrescarDesdeElAlmacen();
    } catch {
      // Sin conexion o error del servidor: el cambio ya quedo en el outbox
      // local (marcarPendiente ya lo aplico y se ve al instante); se
      // reintenta en la siguiente sincronizacion, no hace falta mas aviso.
    } finally {
      setSincronizando(false);
    }
  }

  useEffect(() => {
    let cancelado = false;
    sesion.restaurar().then((exito) => {
      if (cancelado) return;
      setAutenticado(exito);
      setUsuario(sesion.usuario);
      setCargando(false);
      // La lista se lee siempre: en modo local es lo unico que hay.
      refrescarDesdeElAlmacen();
      if (exito) {
        sincronizar();
      }
    });
    return () => {
      cancelado = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- solo al montar
  }, [sesion]);

  // Volver a la app trae lo que se haya guardado en el PC mientras tanto. Sin
  // esto solo se enteraba al arrastrar la lista, y "lo guarde en el otro sitio
  // y aqui no esta" es de las cosas que mas desconfianza dan.
  useEffect(() => {
    const suscripcion = AppState.addEventListener('change', (estado) => {
      if (estado === 'active' && tocaSincronizar(ultimaSincronizacion.current, Date.now())) {
        sincronizar();
      }
    });
    return () => suscripcion.remove();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- solo al montar; sincronizar cierra sobre valores estables
  }, []);

  const valor: EstadoApp = {
    cargando,
    autenticado,
    usuario,
    async iniciarConGoogle(decidirImportacion) {
      const resultado = await sesion.iniciarConProveedor('google');
      if (resultado.estado !== 'exito') {
        return resultado;
      }
      // Sin correo no se toca nada: /auth/renovar no devuelve usuario (ver
      // CONTRATO-API.md), asi que no habria con que comparar y, ante la duda,
      // no se tira ni se importa nada.
      if (sesion.usuario) {
        await asentarCuenta(
          almacen,
          identidadDueno(urlApi(), sesion.usuario.email),
          decidirImportacion,
        );
      }
      setAutenticado(true);
      setUsuario(sesion.usuario);
      refrescarDesdeElAlmacen();
      sincronizar();
      return resultado;
    },
    async cerrar() {
      await sesion.cerrar();
      // Los enlaces se quedan en el telefono y la app sigue funcionando en modo
      // local. El dueno NO se borra a proposito: si manana entra otra cuenta,
      // asentarCuenta sabe que esto era de alguien y pregunta antes de
      // mezclarlo. Volver a entrar con la misma cuenta no pregunta nada.
      setAutenticado(false);
      setUsuario(null);
      refrescarDesdeElAlmacen();
    },

    elementos,
    sincronizando,
    anadir(datos) {
      almacen.marcarPendiente(nuevoElementoLocal(datos));
      refrescarDesdeElAlmacen();
      sincronizar();
    },
    eliminar(id) {
      const actual = almacen.cargarTodos()[id];
      if (!actual) return;
      almacen.marcarPendiente(marcarBorrado(actual));
      refrescarDesdeElAlmacen();
      sincronizar();
    },
    editarEtiquetas(id, etiquetas) {
      const actual = almacen.cargarTodos()[id];
      if (!actual) return;
      almacen.marcarPendiente(editar(actual, { etiquetas }));
      refrescarDesdeElAlmacen();
      sincronizar();
    },
    sincronizar,
    comprobarMetadatos(url) {
      // Con cuenta los resuelve el servidor, que es quien los guarda para los
      // dos clientes; sin cuenta, el propio telefono (ver src/metadatos/).
      return sesion.autenticado
        ? sesion.conReintento((token) => cliente.metadatos(url, token))
        : resolverMetadatosEnDispositivo(url);
    },
  };

  return <ContextoApp.Provider value={valor}>{children}</ContextoApp.Provider>;
}

function useContextoApp(): EstadoApp {
  const contexto = useContext(ContextoApp);
  if (!contexto) {
    throw new Error('Este hook debe usarse dentro de <ProveedorApp>');
  }
  return contexto;
}

export function useSesion() {
  const { cargando, autenticado, usuario, iniciarConGoogle, cerrar } = useContextoApp();
  return { cargando, autenticado, usuario, iniciarConGoogle, cerrar };
}

export function useElementos() {
  const { elementos, sincronizando } = useContextoApp();
  return { elementos, sincronizando };
}

export function useAcciones() {
  const { anadir, eliminar, editarEtiquetas, sincronizar, comprobarMetadatos } = useContextoApp();
  return { anadir, eliminar, editarEtiquetas, sincronizar, comprobarMetadatos };
}
