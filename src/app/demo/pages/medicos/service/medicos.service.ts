import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface Medico {
  id?: number;
  tipoDocumento: string;
  numeroDocumento: string;
  nombres: string;
  apellidos: string;
  telefono?: string;
  registroProfesional: string;
  especializacion?: { id: number; nombre?: string };
}

export interface MedicoRq {
  id?: number;
  tipoDocumento: string;
  numeroDocumento: string;
  nombres: string;
  apellidos: string;
  telefono?: string;
  registroProfesional: string;
  especializacionId: number;
}

@Injectable({ providedIn: 'root' })
export class MedicosService {

  private base = `${environment.apiUrlAuth}/medico`;

  constructor(private http: HttpClient) { }

  listar(): Observable<Medico[]> {
    return this.http.get<Medico[]>(`${this.base}/all`);
  }

  guardar(item: MedicoRq): Observable<any> {
    return this.http.post(`${this.base}/guardar`, item);
  }

  actualizar(item: MedicoRq): Observable<any> {
    return this.http.post(`${this.base}/actualizar`, item);
  }
}
