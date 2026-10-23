import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface Especializacion {
  id?: number;
  nombre: string;
  descripcion?: string;
  codigoEspecializacion: string;
}

@Injectable({ providedIn: 'root' })
export class EspecializacionService {

  private base = `${environment.apiUrlAuth}/especializacion`;

  constructor(private http: HttpClient) { }

  listar(): Observable<Especializacion[]> {
    return this.http.get<Especializacion[]>(`${this.base}/listar`);
  }

  guardar(item: Especializacion): Observable<any> {
    return this.http.post(`${this.base}/guardar`, item);
  }

  actualizar(item: Especializacion): Observable<any> {
    return this.http.post(`${this.base}/actualizar`, item);
  }
}
