import { describe, expect, it } from 'vitest';
import { esUrlYoutube } from '../metadatos/youtube';

describe('esUrlYoutube', () => {
  it.each([
    ['https://www.youtube.com/watch?v=abc', true],
    ['https://youtube.com/watch?v=abc', true],
    ['https://youtu.be/abc', true],
    ['https://m.youtube.com/watch?v=abc', true],
    ['https://vimeo.com/12345', false],
    ['no-es-una-url', false],
  ])('%s -> %s', (url, esperado) => {
    expect(esUrlYoutube(url)).toBe(esperado);
  });
});
