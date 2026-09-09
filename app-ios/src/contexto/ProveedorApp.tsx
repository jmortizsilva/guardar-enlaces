/**
 * Composition root: cablea sesion + almacen + sincronizador y los expone a
 * las pantallas via tres hooks (useSesion, useElementos, useAcciones).
 * Equivalente TS de AplicacionGuardarEnlaces.OnInit en app-windows/.
 */
import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from 'react';

import { ClienteApi, RespuestaMetadatos, UsuarioApi } from '../api/clienteApi';
import { AlmacenLocal } from '../almacen/almacenLocal';
import {
  DatosElementoNuevo,
  Elemento,
  editar,
  marcarBorrado,
  nuevoElementoLocal,
} from '../dominio/elemento';
import { elementosVisibles } from '../dominio/sincronizacion';
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

/**
 * Vacia la cache local si estos datos no son de quien acaba de entrar.
 *
 * La cache NO esta separada por cuenta: sin esto, al cambiar de usuario (o el
 * mismo correo contra otro servidor) se verian mezclados los enlaces del
 * anterior, lo que quedara en el outbox se subiria a la cuenta nueva, y el
 * cursor de sincronizacion —una marca de tiempo del OTRO servidor— haria que
 * el primer pull se saltara todo lo anterior a esa fecha.
 *
 * El dueno lleva tambien la URL del servidor: el mismo correo en el servidor
 * de pruebas y en el real son dos bibliotecas distintas.
 *
 * Sin correo no se compara y no se borra nada: /auth/renovar no devuelve
 * usuario (ver CONTRATO-API.md), asi que al restaurar una sesion guardada no
 * hay con que comparar, y ante la duda no se tira la cache.
 */
function asegurarDueno(almacen: AlmacenLocal, usuario: UsuarioApi | null): void {
  if (!usuario) {
    return;
  }
  const dueno = `${urlApi()}|${usuario.email}`;
  if (almacen.duenoActual() !== dueno) {
    almacen.vaciar();
    almacen.fijarDueno(dueno);
  }
}

interface EstadoApp {
  /** true mientras se intenta restaurar una sesion previa al arrancar. */
  cargando: boolean;
  autenticado: boolean;
  usuario: UsuarioApi | null;
  /**
   * Abre el login de Google. No lanza si el usuario cancela o el proveedor
   * rechaza: eso vuelve en el resultado, para que la pantalla lo anuncie.
   */
  iniciarConGoogle: () => Promise<ResultadoLogin>;
  cerrar: () => Promise<void>;

  /** Los no borrados, mas recientes primero (dominio/sincronizacion.elementosVisibles). */
  elementos: Elemento[];
  sincronizando: boolean;
  anadir: (datos: DatosElementoNuevo) => void;
  eliminar: (id: string) => void;
  editarEtiquetas: (id: string, etiquetas: string[]) => void;
  /** Sincroniza ya, en vez de esperar a la siguiente accion. Nunca lanza (ver comentario interno). */
  sincronizar: () => Promise<void>;
  /** POST /metadatos, para la vista previa de "Añadir enlace". Si lanza, es un ErrorApi real (aqui si se propaga). */
  comprobarMetadatos: (url: string) => Promise<RespuestaMetadatos>;
}

const ContextoApp = createContext<EstadoApp | null>(null);

export function ProveedorApp({ children }: { children: ReactNode }) {
  const cliente = useMemo(() => new ClienteApi(urlApi()), []);
  const sesion = useMemo(() => new Sesion(cliente), [cliente]);
  const almacen = useMemo(() => new AlmacenLocal(), []);
  const sincronizador = useMemo(
    () => new Sincronizador(almacen, cliente, sesion),
    [almacen, cliente, sesion],
  );

  const [cargando, setCargando] = useState(true);
  const [autenticado, setAutenticado] = useState(false);
  const [usuario, setUsuario] = useState<UsuarioApi | null>(null);
  const [elementos, setElementos] = useState<Elemento[]>([]);
  const [sincronizando, setSincronizando] = useState(false);

  function refrescarDesdeElAlmacen(): void {
    setElementos(elementosVisibles(almacen.cargarTodos()));
  }

  async function sincronizar(): Promise<void> {
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
      asegurarDueno(almacen, sesion.usuario);
      setAutenticado(exito);
      setUsuario(sesion.usuario);
      setCargando(false);
      if (exito) {
        refrescarDesdeElAlmacen();
        sincronizar();
      }
    });
    return () => {
      cancelado = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- solo al montar
  }, [sesion]);

  const valor: EstadoApp = {
    cargando,
    autenticado,
    usuario,
    async iniciarConGoogle() {
      const resultado = await sesion.iniciarConProveedor('google');
      if (resultado.estado !== 'exito') {
        return resultado;
      }
      asegurarDueno(almacen, sesion.usuario);
      setAutenticado(true);
      setUsuario(sesion.usuario);
      refrescarDesdeElAlmacen();
      sincronizar();
      return resultado;
    },
    async cerrar() {
      await sesion.cerrar();
      // Los enlaces son de quien se va: no se quedan en el telefono para que
      // los vea el siguiente. Vuelven del servidor al entrar otra vez.
      almacen.vaciar();
      setAutenticado(false);
      setUsuario(null);
      setElementos([]);
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
      return sesion.conReintento((token) => cliente.metadatos(url, token));
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
