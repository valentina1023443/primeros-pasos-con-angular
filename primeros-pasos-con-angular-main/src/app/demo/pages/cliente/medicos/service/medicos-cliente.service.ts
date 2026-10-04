import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Medico } from 'src/app/models/medico';
import { Cita } from 'src/app/models/cita';
import { LocalDbService } from 'src/app/services/local-db.service';

@Injectable({
  providedIn: 'root'
})
export class MedicosClienteService {
  private db = inject(LocalDbService);
  private readonly tabla = 'medicos';

  constructor() {
    // Médicos de ejemplo la primera vez que se abre la app.
    this.db.sembrar<Medico>(this.tabla, [
      { nombre: 'Dra. Laura Gómez', especialidad: 'Medicina general', telefono: '3001234567', email: 'laura@clinica.com', activo: true },
      { nombre: 'Dr. Carlos Rojas', especialidad: 'Cirugía', telefono: '3017654321', email: 'carlos@clinica.com', activo: true }
    ]);
  }

  getMedicos(): Observable<Medico[]> {
    return of(this.db.listar<Medico>(this.tabla));
  }

  async guardar(medico: Medico): Promise<Medico> {
    if (medico.id) return this.db.actualizar<Medico>(this.tabla, medico);
    return this.db.insertar<Medico>(this.tabla, medico);
  }

  async eliminar(id: number): Promise<void> {
    if (this.db.listar<Cita>('citas').some((c) => c.medicoId === id && c.estado === 'PROGRAMADA')) {
      throw new Error('Este médico tiene citas programadas; cancélalas o reasígnalas primero');
    }
    this.db.eliminar<Medico>(this.tabla, id);
  }
}
