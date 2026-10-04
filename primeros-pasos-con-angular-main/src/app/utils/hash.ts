/**
 * Devuelve un hash SHA-256 en hexadecimal (si el navegador lo permite).
 * En contextos no seguros (http fuera de localhost) usa un hash simple de respaldo.
 */
export async function hashTexto(texto: string): Promise<string> {
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    const datos = new TextEncoder().encode(texto);
    const resumen = await crypto.subtle.digest('SHA-256', datos);
    return Array.from(new Uint8Array(resumen))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  }
  let h = 5381;
  for (let i = 0; i < texto.length; i++) {
    h = ((h << 5) + h + texto.charCodeAt(i)) | 0;
  }
  return 'x' + (h >>> 0).toString(16);
}
