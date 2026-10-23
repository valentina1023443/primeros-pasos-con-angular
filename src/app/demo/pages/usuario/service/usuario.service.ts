import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Usuario } from 'src/app/models/usuario';
import { BackendService } from 'src/app/services/backend.service';
import { environment } from 'src/environments/environment';

export interface UsuarioRq {
  id?: number;
  username: string;
  password?: string;
  rol: string;
  activo?: boolean;
  email?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UsuarioService {
  private api = `usuario`;

  constructor(private backendService: BackendService) { }

  getUsuarios(): Observable<Usuario[]> {
    return this.backendService.get(environment.apiUrlAuth, this.api, 'listar-ordenado');
  }

  guardar(usuario: UsuarioRq): Observable<any> {
    return this.backendService.post(environment.apiUrlAuth, this.api, 'guardar', usuario);
  }

  actualizar(usuario: UsuarioRq): Observable<any> {
    return this.backendService.post(environment.apiUrlAuth, this.api, 'actualizar', usuario);
  }
}
