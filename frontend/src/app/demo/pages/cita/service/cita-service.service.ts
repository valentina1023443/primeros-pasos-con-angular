// demo/pages/cita/service/cita-service.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface Cita {
  id?: number;
  mascota?: { mascotaId: number; nombreMascota?: string };
  medico?: { id: number; nombres?: string };
  fecha: string;
  motivo: string;
  observaciones?: string;
}

export interface CitaRq {
  id?: number;
  mascotaId: number;
  medicoId: number;
  fecha: string;
  motivo: string;
  observaciones?: string;
}

@Injectable({ providedIn: 'root' })
export class CitaServiceService {

  private base = `${environment.apiUrlAuth}/cita`;

  constructor(private http: HttpClient) { }

  // Trae "todas" usando un rango amplio, ya que el backend solo expone filtrar por fecha.
  listar(): Observable<Cita[]> {
    const params = new HttpParams()
      .set('fechaInicio', '2000-01-01T00:00:00')
      .set('fechaFin', '2100-12-31T23:59:59');
    return this.http.get<Cita[]>(`${this.base}/filtrar`, { params });
  }

  guardar(cita: CitaRq): Observable<any> {
    return this.http.post(`${this.base}/guardar`, cita);
  }

  actualizar(cita: CitaRq): Observable<any> {
    return this.http.post(`${this.base}/actualizar`, cita);
  }
}