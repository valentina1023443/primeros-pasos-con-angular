import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Mascota } from 'src/app/models/mascota';
import { Usuario } from 'src/app/models/usuario';
import { confirmar, error, exito, mensajeDe } from 'src/app/utils/alertas';
import { UsuariosClienteService } from '../usuarios/service/usuarios-cliente.service';
import { MascotasClienteService } from './service/mascotas-cliente.service';

@Component({
  selector: 'app-mascotas-cliente',
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './mascotas-cliente.component.html'
})
export class MascotasClienteComponent implements OnInit {
  private fb = inject(FormBuilder);
  private servicio = inject(MascotasClienteService);
  private usuarioService = inject(UsuariosClienteService);

  title = 'Gestión de mascotas';
  mascotas: Mascota[] = [];
  propietarios: Usuario[] = [];
  filtro = '';
  mostrarForm = false;
  editandoId: number | null = null;
  guardando = false;

  form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.minLength(2)]],
    especie: ['Perro', Validators.required],
    raza: [''],
    edad: [0, [Validators.required, Validators.min(0), Validators.max(40)]],
    propietarioId: [null as number | null, Validators.required],
    observaciones: ['']
  });

  ngOnInit() {
    this.cargar();
  }

  cargar() {
    this.servicio.getMascotas().subscribe((l) => (this.mascotas = l));
    this.usuarioService.getUsuarios().subscribe((l) => (this.propietarios = l.filter((u) => u.activo)));
  }

  get filtradas(): Mascota[] {
    const f = this.filtro.trim().toLowerCase();
    if (!f) return this.mascotas;
    return this.mascotas.filter((m) =>
      `${m.nombre} ${m.especie} ${m.raza} ${this.nombrePropietario(m.propietarioId)}`.toLowerCase().includes(f)
    );
  }

  nombrePropietario(id?: number) {
    return this.propietarios.find((u) => u.id === id)?.username ?? '—';
  }

  nueva() {
    this.editandoId = null;
    this.form.reset({ nombre: '', especie: 'Perro', raza: '', edad: 0, propietarioId: null, observaciones: '' });
    this.mostrarForm = true;
  }

  editar(m: Mascota) {
    this.editandoId = m.id!;
    this.form.reset({
      nombre: m.nombre ?? '',
      especie: m.especie ?? 'Perro',
      raza: m.raza ?? '',
      edad: m.edad ?? 0,
      propietarioId: m.propietarioId ?? null,
      observaciones: m.observaciones ?? ''
    });
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
      const v = this.form.getRawValue();
      await this.servicio.guardar({
        ...v,
        propietarioId: Number(v.propietarioId),
        id: this.editandoId ?? undefined
      });
      exito(this.editandoId ? 'Mascota actualizada' : 'Mascota registrada');
      this.cancelar();
      this.cargar();
    } catch (e) {
      error(mensajeDe(e));
    } finally {
      this.guardando = false;
    }
  }

  async eliminar(m: Mascota) {
    if (!(await confirmar('¿Eliminar mascota?', `Se eliminará "${m.nombre}" y su historial de citas.`))) return;
    try {
      await this.servicio.eliminar(m.id!);
      exito('Mascota eliminada');
      this.cargar();
    } catch (e) {
      error(mensajeDe(e));
    }
  }
}
