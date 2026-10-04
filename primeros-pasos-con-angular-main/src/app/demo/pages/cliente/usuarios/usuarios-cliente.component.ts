import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Usuario } from 'src/app/models/usuario';
import { confirmar, error, exito, mensajeDe } from 'src/app/utils/alertas';
import { UsuariosClienteService } from './service/usuarios-cliente.service';

@Component({
  selector: 'app-usuarios-cliente',
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './usuarios-cliente.component.html'
})
export class UsuariosClienteComponent implements OnInit {
  private fb = inject(FormBuilder);
  private servicio = inject(UsuariosClienteService);

  usuarios: Usuario[] = [];
  filtro = '';
  mostrarForm = false;
  editandoId: number | null = null;
  guardando = false;

  form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.pattern(/^\S+$/)]],
    email: ['', [Validators.required, Validators.email]],
    rol: ['USUARIO', Validators.required],
    activo: [true],
    password: ['']
  });

  ngOnInit() {
    this.cargar();
  }

  cargar() {
    this.servicio.getUsuarios().subscribe((lista) => (this.usuarios = lista));
  }

  get filtrados(): Usuario[] {
    const f = this.filtro.trim().toLowerCase();
    if (!f) return this.usuarios;
    return this.usuarios.filter((u) => `${u.username} ${u.email} ${u.rol}`.toLowerCase().includes(f));
  }

  esYo(u: Usuario) {
    return false;
  }

  nuevo() {
    this.editandoId = null;
    this.form.reset({ username: '', email: '', rol: 'USUARIO', activo: true, password: '' });
    this.form.controls.password.setValidators([Validators.required, Validators.minLength(6)]);
    this.form.controls.password.updateValueAndValidity();
    this.mostrarForm = true;
  }

  editar(u: Usuario) {
    this.editandoId = u.id!;
    this.form.reset({
      username: u.username ?? '',
      email: u.email ?? '',
      rol: u.rol ?? 'USUARIO',
      activo: u.activo ?? true,
      password: ''
    });
    this.form.controls.password.setValidators([Validators.minLength(6)]);
    this.form.controls.password.updateValueAndValidity();
    this.mostrarForm = true;
  }

  cancelar() {
    this.mostrarForm = false;
    this.editandoId = null;
  }

  async guardar() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.guardando = true;
    try {
      const datos = this.form.getRawValue();
      if (this.editandoId) {
        await this.servicio.actualizar(this.editandoId, datos);
        exito('Usuario actualizado');
      } else {
        await this.servicio.crear(datos);
        exito('Usuario creado');
      }
      this.cancelar();
      this.cargar();
    } catch (e) {
      error(mensajeDe(e));
    } finally {
      this.guardando = false;
    }
  }

  async alternarActivo(u: Usuario) {
    if (this.esYo(u) && u.activo) {
      error('No puedes desactivar tu propio usuario');
      return;
    }
    await this.servicio.actualizar(u.id!, {
      username: u.username!,
      email: u.email!,
      rol: u.rol!,
      activo: !u.activo
    });
    exito(u.activo ? 'Usuario desactivado' : 'Usuario activado');
    this.cargar();
  }

  async eliminar(u: Usuario) {
    if (!(await confirmar('¿Eliminar usuario?', `Se eliminará "${u.username}" de forma permanente.`))) return;
    try {
      await this.servicio.eliminar(u.id!);
      exito('Usuario eliminado');
      this.cargar();
    } catch (e) {
      error(mensajeDe(e));
    }
  }
}
