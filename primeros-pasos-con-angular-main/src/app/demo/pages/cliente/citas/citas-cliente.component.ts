import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Cita } from 'src/app/models/cita';
import { Mascota } from 'src/app/models/mascota';
import { Medico } from 'src/app/models/medico';
import { confirmar, error, exito, mensajeDe } from 'src/app/utils/alertas';
import { MascotasClienteService } from '../mascotas/service/mascotas-cliente.service';
import { MedicosClienteService } from '../medicos/service/medicos-cliente.service';
import { CitasClienteService } from './service/citas-cliente.service';

@Component({
  selector: 'app-citas-cliente',
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './citas-cliente.component.html'
})
export class CitasClienteComponent implements OnInit {
  private fb = inject(FormBuilder);
  private servicio = inject(CitasClienteService);
  private mascotaService = inject(MascotasClienteService);
  private medicosService = inject(MedicosClienteService);

  citas: Cita[] = [];
  mascotas: Mascota[] = [];
  medicos: Medico[] = [];
  filtro = '';
  estadoFiltro = '';
  mostrarForm = false;
  editandoId: number | null = null;
  guardando = false;
  hoy = new Date().toISOString().slice(0, 10);

  form = this.fb.nonNullable.group({
    mascotaId: [null as number | null, Validators.required],
    medicoId: [null as number | null, Validators.required],
    fecha: ['', Validators.required],
    hora: ['', Validators.required],
    motivo: ['', [Validators.required, Validators.minLength(3)]],
    estado: ['PROGRAMADA' as NonNullable<Cita['estado']>, Validators.required]
  });

  ngOnInit() {
    this.cargar();
  }

  cargar() {
    this.servicio.getCitas().subscribe((l) => (this.citas = [...l].sort((a, b) => `${a.fecha}${a.hora}`.localeCompare(`${b.fecha}${b.hora}`))));
    this.mascotaService.getMascotas().subscribe((l) => (this.mascotas = l));
    this.medicosService.getMedicos().subscribe((l) => (this.medicos = l));
  }

  get medicosActivos() {
    return this.medicos.filter((m) => m.activo || m.id === this.form.controls.medicoId.value);
  }

  nombreMascota(id?: number) {
    return this.mascotas.find((m) => m.id === id)?.nombre ?? '—';
  }

  nombreMedico(id?: number) {
    return this.medicos.find((m) => m.id === id)?.nombre ?? '—';
  }

  get filtradas(): Cita[] {
    const f = this.filtro.trim().toLowerCase();
    return this.citas.filter(
      (c) =>
        (!this.estadoFiltro || c.estado === this.estadoFiltro) &&
        (!f || `${this.nombreMascota(c.mascotaId)} ${this.nombreMedico(c.medicoId)} ${c.motivo}`.toLowerCase().includes(f))
    );
  }

  nueva() {
    this.editandoId = null;
    this.form.reset({ mascotaId: null, medicoId: null, fecha: this.hoy, hora: '', motivo: '', estado: 'PROGRAMADA' });
    this.mostrarForm = true;
  }

  editar(c: Cita) {
    this.editandoId = c.id!;
    this.form.reset({
      mascotaId: c.mascotaId ?? null,
      medicoId: c.medicoId ?? null,
      fecha: c.fecha ?? '',
      hora: c.hora ?? '',
      motivo: c.motivo ?? '',
      estado: c.estado ?? 'PROGRAMADA'
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
        mascotaId: Number(v.mascotaId),
        medicoId: Number(v.medicoId),
        id: this.editandoId ?? undefined
      });
      exito(this.editandoId ? 'Cita actualizada' : 'Cita programada');
      this.cancelar();
      this.cargar();
    } catch (e) {
      error(mensajeDe(e));
    } finally {
      this.guardando = false;
    }
  }

  async cambiarEstado(c: Cita, estado: Cita['estado']) {
    try {
      await this.servicio.cambiarEstado(c, estado);
      exito(estado === 'ATENDIDA' ? 'Cita marcada como atendida' : 'Cita cancelada');
      this.cargar();
    } catch (e) {
      error(mensajeDe(e));
    }
  }

  async eliminar(c: Cita) {
    if (!(await confirmar('¿Eliminar cita?', 'Se eliminará la cita de forma permanente.'))) return;
    await this.servicio.eliminar(c.id!);
    exito('Cita eliminada');
    this.cargar();
  }

  claseEstado(e?: string) {
    return e === 'ATENDIDA' ? 'bg-success' : e === 'CANCELADA' ? 'bg-danger' : 'bg-primary';
  }
}
