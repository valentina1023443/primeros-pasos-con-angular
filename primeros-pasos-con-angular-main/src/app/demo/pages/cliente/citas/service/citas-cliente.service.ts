import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Cita } from 'src/app/models/cita';
import { LocalDbService } from 'src/app/services/local-db.service';

@Injectable({
  providedIn: 'root'
})
export class CitasClienteService {
  private db = inject(LocalDbService);
  private readonly tabla = 'citas';

  getCitas(): Observable<Cita[]> {
    return of(this.db.listar<Cita>(this.tabla));
  }

  async guardar(cita: Cita): Promise<Cita> {
    const choque = this.db
      .listar<Cita>(this.tabla)
      .some(
        (c) =>
          c.id !== cita.id &&
          c.estado !== 'CANCELADA' &&
          c.medicoId === cita.medicoId &&
          c.fecha === cita.fecha &&
          c.hora === cita.hora
      );
    if (cita.estado !== 'CANCELADA' && choque) {
      throw new Error('Ese médico ya tiene una cita en esa fecha y hora');
    }
    if (cita.id) return this.db.actualizar<Cita>(this.tabla, cita);
    return this.db.insertar<Cita>(this.tabla, cita);
  }

  async cambiarEstado(cita: Cita, estado: Cita['estado']): Promise<Cita> {
    return this.guardar({ ...cita, estado });
  }

  async eliminar(id: number): Promise<void> {
    this.db.eliminar<Cita>(this.tabla, id);
  }
}
