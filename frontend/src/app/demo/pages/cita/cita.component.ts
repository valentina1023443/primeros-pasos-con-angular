import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TablaDatosComponent, ColumnaTabla } from 'src/app/components/tabla-datos/tabla-datos.component';
import { Cita } from 'src/app/models/cita';
import { NotificacionService } from 'src/app/services/notificacion.service';
import { mensajeDeError } from 'src/app/utils/api-error.util';
import { CitaService } from './service/cita.service';
import { CitaFormComponent } from './cita-form/cita-form.component';

/**
 * Módulo de gestión de citas médicas: listado (DataTable) ordenado por fecha y hora descendente,
 * creación, edición según estado y reactivación de citas canceladas.
 */
@Component({
  selector: 'app-cita',
  standalone: true,
  imports: [CommonModule, TablaDatosComponent],
  templateUrl: './cita.component.html',
  styleUrl: './cita.component.scss'
})
export class CitaComponent implements OnInit {

  /** Citas mostradas en la tabla. */
  citas: Cita[] = [];
  vista: 'lista' | 'dia' | 'semana' | 'mes' = 'lista';
  mesActual = new Date();
  diaActual = new Date();
  /** Indica si se está consultando el backend. */
  cargando = false;

  /** Columnas de la tabla (fecha y hora salen de la misma fecha-hora de la cita). */
  columnas: ColumnaTabla[] = [
    { id: 'fecha', etiqueta: 'Fecha', ruta: 'fechaHora', tipo: 'fecha' },
    { id: 'hora', etiqueta: 'Hora', ruta: 'fechaHora', tipo: 'hora', ordenable: false },
    { id: 'cliente', etiqueta: 'Cliente', ruta: 'clienteNombre', filtro: 'texto' },
    { id: 'mascota', etiqueta: 'Mascota', ruta: 'mascotaNombre', filtro: 'texto' },
    { id: 'veterinario', etiqueta: 'Veterinario', ruta: 'medicoNombre', filtro: 'lista' },
    { id: 'estado', etiqueta: 'Estado', ruta: 'estado', tipo: 'insignia', filtro: 'lista' }
  ];

  constructor(
    private citaService: CitaService,
    private modalService: NgbModal,
    private notificacion: NotificacionService
  ) { }

  /** Carga las citas al abrir la página. */
  ngOnInit(): void {
    this.cargar();
  }

  /** Consulta las citas en el backend. */
  cargar(): void {
    this.cargando = true;
    this.citaService.listar().subscribe({
      next: (data) => { this.citas = data; this.cargando = false; },
      error: (err) => { this.cargando = false; this.notificacion.error(mensajeDeError(err)); }
    });
  }

  get diasCalendario(): Date[] {
    if (this.vista === 'dia') return [new Date(this.diaActual)];
    if (this.vista === 'semana') {
      const start = new Date(this.diaActual); start.setDate(start.getDate() - ((start.getDay() + 6) % 7));
      return Array.from({ length: 7 }, (_, i) => { const d = new Date(start); d.setDate(start.getDate() + i); return d; });
    }
    const first = new Date(this.mesActual.getFullYear(), this.mesActual.getMonth(), 1);
    const start = new Date(first); start.setDate(first.getDate() - ((first.getDay() + 6) % 7));
    return Array.from({ length: 42 }, (_, i) => { const d = new Date(start); d.setDate(start.getDate() + i); return d; });
  }
  get nombreMes(): string { return this.mesActual.toLocaleDateString('es-CO', { month: 'long', year: 'numeric' }); }
  anteriorMes(): void { if(this.vista==='dia') this.diaActual.setDate(this.diaActual.getDate()-1); else if(this.vista==='semana') this.diaActual.setDate(this.diaActual.getDate()-7); else this.mesActual = new Date(this.mesActual.getFullYear(), this.mesActual.getMonth() - 1, 1); this.diaActual=new Date(this.diaActual); }
  siguienteMes(): void { if(this.vista==='dia') this.diaActual.setDate(this.diaActual.getDate()+1); else if(this.vista==='semana') this.diaActual.setDate(this.diaActual.getDate()+7); else this.mesActual = new Date(this.mesActual.getFullYear(), this.mesActual.getMonth() + 1, 1); this.diaActual=new Date(this.diaActual); }
  get tituloCalendario(): string { return this.vista==='mes' ? this.nombreMes : this.diaActual.toLocaleDateString('es-CO',{dateStyle:this.vista==='dia'?'full':'medium'}); }
  private fechaLocal(day: Date): string { return `${day.getFullYear()}-${String(day.getMonth()+1).padStart(2,'0')}-${String(day.getDate()).padStart(2,'0')}`; }
  citasDelDia(day: Date): Cita[] { const key = this.fechaLocal(day); return this.citas.filter(c => c.fechaHora.startsWith(key)); }

  async cancelar(cita: Cita): Promise<void> {
    const confirmado = await this.notificacion.confirmar('¿Cancelar la cita?', 'La cancelación quedará en el historial y no elimina la cita.', 'Cancelar cita');
    if (!confirmado) return;
    const motivo = prompt('Motivo de cancelación:')?.trim();
    if (!motivo) return;
    this.citaService.cancelar(cita.id,motivo).subscribe({ next: () => { this.notificacion.exito('Cancelación registrada.'); this.cargar(); }, error: err => this.notificacion.error(mensajeDeError(err)) });
  }

  /**
   * Abre el formulario (modal) de cita; recarga la tabla si se guardó algo.
   * @param cita cita a editar; si se omite, se crea una nueva.
   */
  abrirFormulario(cita?: Cita): void {
    const ref = this.modalService.open(CitaFormComponent, { size: 'lg', backdrop: 'static' });
    ref.componentInstance.cita = cita;
    ref.result.then((guardado) => { if (guardado) { this.cargar(); } }, () => undefined);
  }

  /**
   * Reactiva una cita cancelada tras pedir confirmación (operación explícita).
   * @param cita cita cancelada.
   */
  async reactivar(cita: Cita): Promise<void> {
    const confirmado = await this.notificacion.confirmar(
      '¿Reactivar la cita?',
      'La cita volverá al estado Pendiente. El sistema validará que el horario siga disponible.',
      'Sí, reactivar'
    );
    if (!confirmado) {
      return;
    }
    this.citaService.reactivar(cita.id).subscribe({
      next: (r) => { this.notificacion.exito(r?.message || 'Cita reactivada correctamente.'); this.cargar(); },
      error: (err) => this.notificacion.error(mensajeDeError(err))
    });
  }
}
