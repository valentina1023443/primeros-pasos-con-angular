import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Mascota } from 'src/app/models/mascota';
import { Cita } from 'src/app/models/cita';
import { LocalDbService } from 'src/app/services/local-db.service';

@Injectable({
  providedIn: 'root'
})
export class MascotasClienteService {
  private db = inject(LocalDbService);
  private readonly tabla = 'mascotas';

  getMascotas(): Observable<Mascota[]> {
    return of(this.db.listar<Mascota>(this.tabla));
  }

  async guardar(mascota: Mascota): Promise<Mascota> {
    if (mascota.id) return this.db.actualizar<Mascota>(this.tabla, mascota);
    return this.db.insertar<Mascota>(this.tabla, { ...mascota, fechaCreacion: new Date() });
  }

  async eliminar(id: number): Promise<void> {
    if (this.db.listar<Cita>('citas').some((c) => c.mascotaId === id && c.estado === 'PROGRAMADA')) {
      throw new Error('Esta mascota tiene citas programadas; cancélalas primero');
    }
    this.db.eliminar<Mascota>(this.tabla, id);
    // Se borra también el historial de citas de la mascota.
    this.db.listar<Cita>('citas').filter((c) => c.mascotaId === id).forEach((c) => this.db.eliminar<Cita>('citas', c.id!));
  }
}
