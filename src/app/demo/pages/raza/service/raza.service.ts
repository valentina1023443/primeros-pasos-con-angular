import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface Raza {
  razaId?: number;
  nombre: string;
  especie: string;
}

@Injectable({ providedIn: 'root' })
export class RazaService {

  private base = `${environment.apiUrlAuth}/raza`;

  constructor(private http: HttpClient) { }

  listar(): Observable<Raza[]> {
    return this.http.get<Raza[]>(`${this.base}/listar`);
  }

  guardar(raza: Raza): Observable<any> {
    return this.http.post(`${this.base}/guardar`, raza);
  }

  actualizar(raza: Raza): Observable<any> {
    return this.http.post(`${this.base}/actualizar`, raza);
  }
}
