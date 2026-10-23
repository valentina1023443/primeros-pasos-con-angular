import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface AnotacionHistoria {
  id?: number;
  historiaMedica?: { id: number };
  descripcion: string;
  fecha?: string;
}

export interface AnotacionHistoriaRq {
  id?: number;
  historiaMedicaId: number;
  descripcion: string;
  fecha?: string;
}

@Injectable({ providedIn: 'root' })
export class AnotacionHistoriaService {

  private base = `${environment.apiUrlAuth}/anotacion-historia`;

  constructor(private http: HttpClient) { }

  listar(): Observable<AnotacionHistoria[]> {
    const params = new HttpParams()
      .set('fechaInicio', '2000-01-01T00:00:00')
      .set('fechaFin', '2100-12-31T23:59:59');
    return this.http.get<AnotacionHistoria[]>(`${this.base}/filtrar`, { params });
  }

  guardar(item: AnotacionHistoriaRq): Observable<any> {
    return this.http.post(`${this.base}/guardar`, item);
  }

  actualizar(item: AnotacionHistoriaRq): Observable<any> {
    return this.http.post(`${this.base}/actualizar`, item);
  }
}
