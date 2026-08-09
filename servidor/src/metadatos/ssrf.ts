import { isIP } from 'node:net';

// Mitigacion basica de SSRF: resolver metadatos implica pedirle al servidor que haga una peticion
// HTTP a una URL que manda el usuario, asi que hay que evitar que apunte a la red interna del
// host (ej. la IP de metadatos de la nube, 169.254.169.254, u otro contenedor del mismo Docker).
// Logica pura: solo mira si una IP ya resuelta cae en un rango privado/reservado.

export function esIpPrivada(ip: string): boolean {
  const version = isIP(ip);

  if (version === 4) {
    const [a, b] = ip.split('.').map(Number);
    if (a === 0 || a === 10 || a === 127) {
      return true;
    }
    if (a === 169 && b === 254) {
      return true; // incluye 169.254.169.254, la IP de metadatos de la mayoria de nubes
    }
    if (a === 172 && b >= 16 && b <= 31) {
      return true;
    }
    if (a === 192 && b === 168) {
      return true;
    }
    return false;
  }

  if (version === 6) {
    const normalizada = ip.toLowerCase();
    if (normalizada === '::1' || normalizada === '::') {
      return true;
    }
    if (normalizada.startsWith('fe80:')) {
      return true; // link-local
    }
    if (normalizada.startsWith('fc') || normalizada.startsWith('fd')) {
      return true; // unique local, fc00::/7
    }
    const mapeada = normalizada.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/);
    if (mapeada) {
      return esIpPrivada(mapeada[1]); // IPv4 mapeada en IPv6
    }
    return false;
  }

  return true; // no es una IP reconocible: por seguridad, se trata como no permitida
}
