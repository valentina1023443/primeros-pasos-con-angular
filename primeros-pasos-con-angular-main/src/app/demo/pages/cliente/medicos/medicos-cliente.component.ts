import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Medico } from 'src/app/models/medico';
import { confirmar, error, exito, mensajeDe } from 'src/app/utils/alertas';
import { MedicosClienteService } from './service/medicos-cliente.service';

@Component({
  selector: 'app-medicos-cliente',
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './medicos-cliente.component.html'
})
export class MedicosClienteComponent implements OnInit {
  private fb = inject(FormBuilder);
  private servicio = inject(MedicosClienteService);

  medicos: Medico[] = [];
  filtro = '';
  mostrarForm = false;
  editandoId: number | null = null;
  guardando = false;

  form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.minLength(3)]],
    especialidad: ['', Validators.required],
    telefono: ['', [Validators.required, Validators.pattern(/^[0-9+\s-]{7,15}$/)]],
    email: ['', [Validators.required, Validators.email]],
    activo: [true]
  });

  ngOnInit() {
    this.cargar();
  }

  cargar() {
    this.servicio.getMedicos().subscribe((l) => (this.medicos = l));
  }

  get filtrados(): Medico[] {
    const f = this.filtro.trim().toLowerCase();
    if (!f) return this.medicos;
    return this.medicos.filter((m) => `${m.nombre} ${m.especialidad} ${m.email}`.toLowerCase().includes(f));
  }

  nuevo() {
    this.editandoId = null;
    this.form.reset({ nombre: '', especialidad: '', telefono: '', email: '', activo: true });
    this.mostrarForm = true;
  }

  editar(m: Medico) {
    this.editandoId = m.id!;
    this.form.reset({
      nombre: m.nombre ?? '',
      especialidad: m.especialidad ?? '',
      telefono: m.telefono ?? '',
      email: m.email ?? '',
      activo: m.activo ?? true
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
      await this.servicio.guardar({ ...this.form.getRawValue(), id: this.editandoId ?? undefined });
      exito(this.editandoId ? 'Médico actualizado' : 'Médico registrado');
      this.cancelar();
      this.cargar();
    } catch (e) {
      error(mensajeDe(e));
    } finally {
      this.guardando = false;
    }
  }

  async eliminar(m: Medico) {
    if (!(await confirmar('¿Eliminar médico?', `Se eliminará a "${m.nombre}".`))) return;
    try {
      await this.servicio.eliminar(m.id!);
      exito('Médico eliminado');
      this.cargar();
    } catch (e) {
      error(mensajeDe(e));
    }
  }
}
