import Swal from 'sweetalert2';

export function exito(mensaje: string) {
  Swal.fire({
    toast: true,
    position: 'top-end',
    icon: 'success',
    title: mensaje,
    showConfirmButton: false,
    timer: 2200
  });
}

export function error(mensaje: string) {
  Swal.fire({ icon: 'error', title: 'Ups', text: mensaje, confirmButtonText: 'Entendido' });
}

export async function confirmar(titulo: string, texto: string): Promise<boolean> {
  const r = await Swal.fire({
    title: titulo,
    text: texto,
    icon: 'warning',
    showCancelButton: true,
    confirmButtonText: 'Sí, continuar',
    cancelButtonText: 'Cancelar'
  });
  return r.isConfirmed;
}

/** Convierte cualquier error capturado en un mensaje legible. */
export function mensajeDe(e: unknown): string {
  return e instanceof Error ? e.message : 'Ocurrió un error inesperado';
}
