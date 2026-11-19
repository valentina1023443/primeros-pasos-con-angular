import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface Medicamento {
  id?: number;
  nombre: string;
  presentacion?: string;
  descripcion?: string;
}

@Injectable({ providedIn: 'root' })
export class MedicamentoService {

  private base = `${environment.apiUrlAuth}/medicamento`;

  constructor(private http: HttpClient) { }

  listar(): Observable<Medicamento[]> {
    return this.http.get<Medicamento[]>(`${this.base}/listar`);
  }

  guardar(item: Medicamento): Observable<any> {
    return this.http.post(`${this.base}/guardar`, item);
  }

  actualizar(item: Medicamento): Observable<any> {
    return this.http.post(`${this.base}/actualizar`, item);
  }
}
