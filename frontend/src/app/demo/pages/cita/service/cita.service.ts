import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { BackendService } from 'src/app/services/backend.service';
import { environment } from 'src/environments/environment';
import { Cita, CitaRq } from 'src/app/models/cita';
import { Respuesta } from 'src/app/models/respuesta';

@Injectable({ providedIn: 'root' })
export class CitaService {
  private readonly endpoint = 'cita';
  private readonly gestion = `${environment.apiUrlAuth}/gestion/citas`;
  constructor(private backend: BackendService, private readonly http: HttpClient) { }

  listar(): Observable<Cita[]> {
    const params = new HttpParams().set('desde','2000-01-01').set('hasta','2099-12-31');
    return this.http.get<any[]>(`${this.gestion}/calendario`, { params }).pipe(map(rows => rows.map(row => this.toCita(row))));
  }

  consultar(id: number): Observable<Cita> { return this.backend.get<Cita>(environment.apiUrlAuth, this.endpoint, `${id}`); }
  listarPorMascota(mascotaId: number): Observable<Cita[]> { return this.backend.get<Cita[]>(environment.apiUrlAuth, this.endpoint, `mascota/${mascotaId}`); }

  crear(cita: CitaRq): Observable<Respuesta> { return this.http.post<Respuesta>(this.gestion, this.toGestionRq(cita)); }
  editar(id: number, cita: CitaRq): Observable<Respuesta> { return this.http.put<Respuesta>(`${this.gestion}/${id}`, this.toGestionRq(cita)); }
  cancelar(id: number, motivo: string): Observable<Respuesta> { return this.http.post<Respuesta>(`${this.gestion}/${id}/cancelar`, { motivo }); }
  reactivar(id: number): Observable<Respuesta> { return this.http.post<Respuesta>(`${this.gestion}/${id}/reactivar`, {}); }

  private toGestionRq(cita: CitaRq): Record<string, unknown> {
    const start = cita.fechaHora.replace(' ', 'T').slice(0,19);
    const startDate = new Date(start);
    const endDate = new Date(startDate.getTime() + 30 * 60 * 1000);
    return {
      clienteId: cita.clienteId, mascotaId: cita.mascotaId, medicoId: cita.medicoId,
      inicio: start, fin: this.toLocalIso(endDate), motivo: cita.motivo,
      observaciones: cita.observaciones, estado: cita.estado
    };
  }

  private toLocalIso(value: Date): string {
    const pad = (part: number) => String(part).padStart(2,'0');
    return `${value.getFullYear()}-${pad(value.getMonth()+1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`;
  }

  private toCita(row: any): Cita {
    const fecha = String(row.fecha ?? '').replace(' ','T').slice(0,19);
    return {
      id: Number(row.id), fechaHora: fecha, clienteId: Number(row.cliente_id), clienteNombre: row.propietario,
      mascotaId: Number(row.mascota_id), mascotaNombre: row.mascota,
      medicoId: Number(row.medico_id), medicoNombre: row.veterinario,
      motivo: row.motivo, estado: row.estado, observaciones: row.observaciones
    };
  }
}
