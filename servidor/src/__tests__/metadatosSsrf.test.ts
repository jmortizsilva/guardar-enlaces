import { describe, expect, it } from 'vitest';
import { esIpPrivada } from '../metadatos/ssrf';

describe('esIpPrivada', () => {
  it.each([
    ['10.0.0.5', true],
    ['172.16.0.1', true],
    ['172.31.255.255', true],
    ['172.32.0.1', false], // fuera del rango 172.16-31
    ['192.168.1.1', true],
    ['127.0.0.1', true],
    ['169.254.169.254', true], // IP de metadatos de la nube
    ['0.0.0.0', true],
    ['8.8.8.8', false],
    ['1.1.1.1', false],
  ])('IPv4 %s -> %s', (ip, esperado) => {
    expect(esIpPrivada(ip)).toBe(esperado);
  });

  it.each([
    ['::1', true],
    ['fe80::1', true],
    ['fc00::1', true],
    ['fd12:3456::1', true],
    ['::ffff:127.0.0.1', true],
    ['2001:4860:4860::8888', false], // DNS publico de Google en IPv6
  ])('IPv6 %s -> %s', (ip, esperado) => {
    expect(esIpPrivada(ip)).toBe(esperado);
  });

  it('una cadena que no es una IP se trata como no permitida', () => {
    expect(esIpPrivada('no-es-una-ip')).toBe(true);
  });
});
