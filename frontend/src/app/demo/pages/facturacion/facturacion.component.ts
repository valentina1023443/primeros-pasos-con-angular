import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { environment } from 'src/environments/environment';
import { AuthService } from 'src/app/services/auth.service';
import { NotificacionService } from 'src/app/services/notificacion.service';

interface CitaFacturable {
  cita_id: number;
  fecha: string;
  mascota: string;
  propietario: string;
  veterinario: string;
  especialidad: string | null;
  especializacion_id: number | null;
}

interface Tarifa {
  id: number;
  servicio: string;
  valor: number;
  iva_porcentaje: number;
  especializacion_id: number | null;
  especialidad: string | null;
  activa: boolean | number;
  vigente_desde: string;
  vigente_hasta: string | null;
}

interface Factura {
  factura_id: number;
  cita_id: number;
  fecha_atencion: string;
  propietario: string;
  mascota: string;
  veterinario: string;
  especialidad: string | null;
  subtotal: number;
  iva: number;
  descuento: number;
  total: number;
  pagado: number;
  saldo: number;
  estado: string;
}

interface PagoFactura {
  id: number;
  factura_id: number;
  valor: number;
  medio: string;
  usuario: string;
  pagado_en: string;
  operacion_id: string;
  tipo: 'PAGO' | 'REVERSO';
  pago_original_id: number | null;
  motivo_reversion: string | null;
}

interface PagoPendiente {
  valor: number;
  medio: string;
  operacionId: string;
}

interface ReversionPendiente {
  facturaId: number;
  pagoId: number;
  valor: number;
  medio: string;
  motivo: string;
  operacionId: string;
}

@Component({
  selector: 'app-facturacion',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './facturacion.component.html'
})
export class FacturacionComponent implements OnInit {
  private readonly api = `${environment.apiUrlAuth}/operaciones`;
  citas: CitaFacturable[] = [];
  tarifas: Tarifa[] = [];
  facturas: Factura[] = [];
  pagos: Record<number, PagoFactura[]> = {};
  pagosPendientes: Record<number, PagoPendiente> = {};
  reversionesPendientes: Record<number, ReversionPendiente> = {};
  citasCargando = false;
  tarifasCargando = false;
  facturasCargando = false;
  creandoFactura = false;
  error = '';
  filtro = '';
  citaSeleccionada = '';
  tarifaSeleccionada = '';
  descuento = 0;
  motivoDescuento = '';
  montoPago: Record<number, string> = {};
  medioPago: Record<number, string> = {};
  pagando = new Set<number>();
  reversando = new Set<number>();
  expanded = new Set<number>();

  constructor(
    private readonly http: HttpClient,
    private readonly auth: AuthService,
    private readonly notificacion: NotificacionService
  ) {}

  ngOnInit(): void {
    this.cargar();
    this.cargarTarifas();
  }

  get esAdministrador(): boolean { return this.auth.hasRole('ADMINISTRADOR'); }
  get hayDescuento(): boolean { return Number(this.descuento) > 0; }

  get tarifasDisponibles(): Tarifa[] {
    const cita = this.citas.find(row => Number(row.cita_id) === Number(this.citaSeleccionada));
    if (!cita) return [];
    const now = new Date();
    const hoy = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    return this.tarifas.filter(t => {
      const activa = t.activa === true || String(t.activa) === '1' || String(t.activa).toLowerCase() === 'true';
      const aplicable = t.especializacion_id == null || Number(t.especializacion_id) === Number(cita.especializacion_id);
      const vigente = String(t.vigente_desde).slice(0, 10) <= hoy && (!t.vigente_hasta || String(t.vigente_hasta).slice(0, 10) >= hoy);
      return activa && aplicable && vigente;
    }).sort((a, b) => {
      const aConsulta = a.servicio.toLocaleLowerCase() === 'consulta veterinaria' ? 0 : 1;
      const bConsulta = b.servicio.toLocaleLowerCase() === 'consulta veterinaria' ? 0 : 1;
      if (aConsulta !== bConsulta) return aConsulta - bConsulta;
      const aEspecifica = a.especializacion_id == null ? 1 : 0;
      const bEspecifica = b.especializacion_id == null ? 1 : 0;
      return aEspecifica - bEspecifica || b.id - a.id;
    });
  }

  cargar(): void {
    this.error = '';
    this.cargarCitas();
    this.cargarFacturas();
  }

  cargarCitas(): void {
    this.citasCargando = true;
    this.http.get<CitaFacturable[]>(`${this.api}/facturas/citas-atendidas`).subscribe({
      next: rows => {
        this.citas = rows;
        this.citasCargando = false;
        this.seleccionarTarifaPorCita();
      },
      error: err => { this.citasCargando = false; this.mostrarError(err); }
    });
  }

  cargarTarifas(): void {
    this.tarifasCargando = true;
    this.http.get<Tarifa[]>(`${environment.apiUrlAuth}/gestion/tarifas`).subscribe({
      next: rows => {
        this.tarifas = rows;
        this.tarifasCargando = false;
        this.seleccionarTarifaPorCita();
      },
      error: err => { this.tarifasCargando = false; this.mostrarError(err); }
    });
  }

  seleccionarTarifaPorCita(): void {
    const disponibles = this.tarifasDisponibles;
    if (!disponibles.some(t => Number(t.id) === Number(this.tarifaSeleccionada))) {
      this.tarifaSeleccionada = disponibles.length ? String(disponibles[0].id) : '';
    }
  }

  cargarFacturas(): void {
    this.facturasCargando = true;
    const params = new HttpParams().set('texto', this.filtro.trim());
    this.http.get<Factura[]>(`${this.api}/facturas`, { params }).subscribe({
      next: rows => {
        this.facturas = rows;
        this.facturasCargando = false;
        for (const factura of rows) this.restaurarPagoPendiente(factura.factura_id);
      },
      error: err => { this.facturasCargando = false; this.mostrarError(err); }
    });
  }

  crearFactura(): void {
    const citaId = Number(this.citaSeleccionada);
    const tarifaId = Number(this.tarifaSeleccionada);
    if (!citaId || !tarifaId || this.creandoFactura) return;
    if (this.esAdministrador && Number(this.descuento) > 0 && !this.motivoDescuento.trim()) {
      this.notificacion.error('Indique el motivo del descuento administrativo.');
      return;
    }
    const body: Record<string, unknown> = { citaId, tarifaId };
    if (this.esAdministrador && Number(this.descuento) > 0) {
      body['descuento'] = Number(this.descuento);
      body['motivoDescuento'] = this.motivoDescuento.trim();
    }
    this.creandoFactura = true;
    this.http.post<{ facturaId: number; total: number; servicio: string }>(`${this.api}/facturas`, body).subscribe({
      next: result => {
        this.creandoFactura = false;
        this.citaSeleccionada = '';
        this.tarifaSeleccionada = '';
        this.descuento = 0;
        this.motivoDescuento = '';
        this.notificacion.exito(`Factura #${result.facturaId} (${result.servicio}) creada por ${this.moneda(result.total)}.`);
        this.cargar();
      },
      error: err => { this.creandoFactura = false; this.mostrarError(err); }
    });
  }

  registrarPago(factura: Factura): void {
    const facturaId = factura.factura_id;
    if (this.pagando.has(facturaId)) return;
    let pending = this.pagosPendientes[facturaId] ?? this.leerPendiente<PagoPendiente>('pago', facturaId);
    if (pending) {
      this.pagosPendientes[facturaId] = pending;
      this.montoPago[facturaId] = String(pending.valor);
      this.medioPago[facturaId] = pending.medio;
    } else {
      const valor = Number(this.montoPago[facturaId]);
      if (!Number.isFinite(valor) || valor <= 0) {
        this.notificacion.error('Ingrese un valor de pago mayor que cero.');
        return;
      }
      const medio = this.medioPago[facturaId] || 'EFECTIVO';
      pending = { valor, medio, operacionId: this.nuevaOperacion() };
      this.pagosPendientes[facturaId] = pending;
      this.guardarPendiente('pago', facturaId, pending);
    }
    this.pagando.add(facturaId);
    this.http.post(`${this.api}/facturas/${facturaId}/pagos`, pending).subscribe({
      next: () => {
        this.pagando.delete(facturaId);
        delete this.pagosPendientes[facturaId];
        this.eliminarPendiente('pago', facturaId);
        this.montoPago[facturaId] = '';
        this.notificacion.exito('Pago registrado o confirmado por su clave idempotente.');
        this.cargarFacturas();
        if (this.expanded.has(facturaId)) this.cargarPagos(facturaId);
      },
      error: err => {
        this.pagando.delete(facturaId);
        if (this.esRespuestaDefinitiva(err)) {
          delete this.pagosPendientes[facturaId];
          this.eliminarPendiente('pago', facturaId);
        }
        this.mostrarError(err);
      }
    });
  }

  alternarPagos(facturaId: number): void {
    if (this.expanded.has(facturaId)) {
      this.expanded.delete(facturaId);
      return;
    }
    this.expanded.add(facturaId);
    this.cargarPagos(facturaId);
  }

  private cargarPagos(facturaId: number): void {
    this.http.get<PagoFactura[]>(`${this.api}/facturas/${facturaId}/pagos`).subscribe({
      next: rows => {
        this.pagos[facturaId] = rows;
        for (const pago of rows) {
          if (pago.tipo !== 'PAGO') continue;
          const pending = this.leerPendiente<ReversionPendiente>('reversion', pago.id);
          if (pending) this.reversionesPendientes[pago.id] = pending;
        }
      },
      error: err => this.mostrarError(err)
    });
  }

  restanteReversion(facturaId: number, pago: PagoFactura): number {
    const devuelto = (this.pagos[facturaId] ?? [])
      .filter(mov => mov.tipo === 'REVERSO' && Number(mov.pago_original_id) === Number(pago.id))
      .reduce((sum, mov) => sum + Math.abs(Number(mov.valor)), 0);
    return Math.max(0, Number(pago.valor) - devuelto);
  }

  async reversar(facturaId: number, pago: PagoFactura): Promise<void> {
    if (!this.esAdministrador || pago.tipo !== 'PAGO' || this.reversando.has(pago.id)) return;
    let pending = this.reversionesPendientes[pago.id] ?? this.leerPendiente<ReversionPendiente>('reversion', pago.id);
    if (!pending) {
      const disponible = this.restanteReversion(facturaId, pago);
      if (disponible <= 0) return;
      const textoValor = prompt(`Valor a reembolsar (máximo ${this.moneda(disponible)}):`, String(disponible));
      if (textoValor === null) return;
      const valor = Number(textoValor);
      if (!Number.isFinite(valor) || valor <= 0 || valor > disponible) {
        this.notificacion.error('El valor debe ser positivo y no puede superar el pago original.');
        return;
      }
      const motivo = prompt('Motivo de la reversión:')?.trim();
      if (!motivo) return;
      const confirmar = await this.notificacion.confirmar('¿Registrar el reembolso?', `${this.moneda(valor)} se devolverán por ${pago.medio}. El movimiento quedará auditado.`, 'Registrar reembolso');
      if (!confirmar) return;
      pending = { facturaId, pagoId: pago.id, valor, medio: pago.medio, motivo, operacionId: this.nuevaOperacion() };
      this.reversionesPendientes[pago.id] = pending;
      this.guardarPendiente('reversion', pago.id, pending);
    }
    this.reversando.add(pago.id);
    this.http.post(`${this.api}/facturas/${facturaId}/pagos/${pago.id}/reversiones`, pending).subscribe({
      next: () => {
        this.reversando.delete(pago.id);
        delete this.reversionesPendientes[pago.id];
        this.eliminarPendiente('reversion', pago.id);
        this.notificacion.exito('Reversión registrada o confirmada por su clave idempotente.');
        this.cargarFacturas();
        this.cargarPagos(facturaId);
      },
      error: err => {
        this.reversando.delete(pago.id);
        if (this.esRespuestaDefinitiva(err)) {
          delete this.reversionesPendientes[pago.id];
          this.eliminarPendiente('reversion', pago.id);
        }
        this.mostrarError(err);
      }
    });
  }

  async anular(factura: Factura): Promise<void> {
    if (!this.esAdministrador) return;
    const motivo = prompt('Motivo de anulación:')?.trim();
    if (!motivo) return;
    const confirmar = await this.notificacion.confirmar('¿Anular la factura?', 'La anulación no elimina el documento ni su historial.', 'Anular factura');
    if (!confirmar) return;
    this.http.put(`${this.api}/facturas/${factura.factura_id}/anular`, { motivo }).subscribe({
      next: () => { this.notificacion.exito('Factura anulada.'); this.cargarFacturas(); },
      error: err => this.mostrarError(err)
    });
  }

  descargar(facturaId: number): void {
    this.http.get(`${environment.apiUrlAuth}/gestion/documentos/factura/${facturaId}`, { responseType: 'blob' }).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `factura-${facturaId}.pdf`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: err => this.mostrarError(err)
    });
  }

  moneda(value: number | string | null | undefined): string {
    return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(Number(value || 0));
  }

  private restaurarPagoPendiente(facturaId: number): void {
    const pending = this.leerPendiente<PagoPendiente>('pago', facturaId);
    if (!pending) return;
    this.pagosPendientes[facturaId] = pending;
    this.montoPago[facturaId] = String(pending.valor);
    this.medioPago[facturaId] = pending.medio;
  }

  private storageKey(tipo: string, id: number): string {
    const userId = localStorage.getItem('userId') || 'usuario';
    return `clinica-veterinaria:${userId}:${tipo}:${id}`;
  }

  private leerPendiente<T>(tipo: string, id: number): T | null {
    try {
      const raw = sessionStorage.getItem(this.storageKey(tipo, id));
      return raw ? JSON.parse(raw) as T : null;
    } catch { return null; }
  }

  private guardarPendiente(tipo: string, id: number, value: unknown): void {
    try { sessionStorage.setItem(this.storageKey(tipo, id), JSON.stringify(value)); } catch { /* La clave se conserva en memoria de la pantalla. */ }
  }

  private eliminarPendiente(tipo: string, id: number): void {
    try { sessionStorage.removeItem(this.storageKey(tipo, id)); } catch { /* Sin almacenamiento de sesión, la pantalla sigue operativa. */ }
  }

  private esRespuestaDefinitiva(err: any): boolean {
    return Number(err?.status) >= 400 && Number(err?.status) < 500;
  }

  private nuevaOperacion(): string {
    return globalThis.crypto?.randomUUID?.() ?? `op-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
  }

  private mostrarError(err: any): void {
    this.error = err?.error?.message || err?.message || 'No fue posible completar la operación.';
    this.notificacion.error(this.error);
  }
}
