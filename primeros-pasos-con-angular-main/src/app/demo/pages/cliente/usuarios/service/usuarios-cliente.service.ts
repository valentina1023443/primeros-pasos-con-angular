import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Usuario } from 'src/app/models/usuario';
import { Mascota } from 'src/app/models/mascota';
import { LocalDbService } from 'src/app/services/local-db.service';
import { hashTexto } from 'src/app/utils/hash';

export interface UsuarioForm {
  username: string;
  email: string;
  rol: string;
  activo: boolean;
  password?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UsuariosClienteService {
  private db = inject(LocalDbService);
  private readonly tabla = 'usuarios';

  getUsuarios(): Observable<Usuario[]> {
    return of(this.db.listar<Usuario>(this.tabla));
  }

  private validarUnico(datos: UsuarioForm, idActual?: number) {
    const otros = this.db.listar<Usuario>(this.tabla).filter((u) => u.id !== idActual);
    if (otros.some((u) => u.username?.toLowerCase() === datos.username.toLowerCase())) {
      throw new Error('Ese nombre de usuario ya está registrado');
    }
    if (otros.some((u) => u.email?.toLowerCase() === datos.email.toLowerCase())) {
      throw new Error('Ese correo ya está registrado');
    }
  }

  async crear(datos: UsuarioForm): Promise<Usuario> {
    this.validarUnico(datos);
    return this.db.insertar<Usuario>(this.tabla, {
      username: datos.username,
      email: datos.email,
      rol: datos.rol,
      activo: datos.activo,
      password_hash: await hashTexto(datos.password ?? ''),
      fechaCreacion: new Date()
    });
  }

  async actualizar(id: number, datos: UsuarioForm): Promise<Usuario> {
    this.validarUnico(datos, id);
    const cambios: Usuario = { id, username: datos.username, email: datos.email, rol: datos.rol, activo: datos.activo };
    if (datos.password) cambios.password_hash = await hashTexto(datos.password);
    return this.db.actualizar<Usuario>(this.tabla, cambios);
  }

  async eliminar(id: number): Promise<void> {
    if (this.db.listar<Mascota>('mascotas').some((m) => m.propietarioId === id)) {
      throw new Error('Este usuario tiene mascotas registradas; elimínalas o reasígnalas primero');
    }
    this.db.eliminar<Usuario>(this.tabla, id);
  }
}
