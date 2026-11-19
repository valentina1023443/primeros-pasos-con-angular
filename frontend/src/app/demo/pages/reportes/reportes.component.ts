import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface ReportPreview {
  tipo: string;
  desde: string;
  hasta: string;
  generadoEn: string;
  cantidad: number;
  columnas: string[];
  filas: Array<Record<string, unknown>>;
  totales: Record<string, unknown>;
}

@Component({ selector: 'app-reportes', standalone: true, imports: [CommonModule, FormsModule], templateUrl: './reportes.component.html', styleUrl: './reportes.component.scss' })
export class ReportesComponent {
  tipo = 'financiero';
  formato: 'xlsx'|'pdf' = 'xlsx';
  desde = new Date().toISOString().slice(0,10);
  hasta = this.desde;
  estado = '';
  medicoId: number | null = null;
  cargando = false;
  exportando = false;
  error = '';
  preview?: ReportPreview;
  tipos = [
    { id:'financiero', label:'Ingresos y cuentas por cobrar' },
    { id:'facturas', label:'Detalle de facturas y pagos' },
    { id:'ingresos-dia', label:'Ingresos cobrados por día' },
    { id:'ingresos-mes', label:'Ingresos cobrados por mes' },
    { id:'ingresos-anio', label:'Ingresos cobrados por año' },
    { id:'ingresos-especialidad', label:'Ingresos por especialidad' },
    { id:'ingresos-veterinario', label:'Ingresos por veterinario' },
    { id:'ventas-medicamentos', label:'Medicamentos y productos dispensados' },
    { id:'inventario', label:'Estado y valoración de inventario' },
    { id:'movimientos', label:'Entradas y salidas de inventario' },
    { id:'medico', label:'Gestión médica por estado' },
    { id:'citas', label:'Citas detalladas' }
  ];
  estados = ['PENDIENTE','PARCIAL','PAGADA','ANULADA','CONFIRMADA','PROGRAMADA','ATENDIDA','CANCELADA'];
  constructor(private readonly http: HttpClient) {}

  private params(): HttpParams {
    let params = new HttpParams().set('desde', this.desde).set('hasta', this.hasta);
    if (this.estado) params = params.set('estado', this.estado);
    if (this.medicoId) params = params.set('medicoId', this.medicoId);
    return params;
  }

  previsualizar(): void {
    if (!this.validarRango()) return;
    this.cargando = true; this.error = ''; this.preview = undefined;
    this.http.get<ReportPreview>(`${environment.apiUrlAuth}/gestion/reportes/${this.tipo}/preview`, { params: this.params() }).subscribe({
      next: result => { this.preview = result; this.cargando = false; },
      error: error => { this.error = error?.error?.message || 'No fue posible preparar la vista previa.'; this.cargando = false; }
    });
  }

  descargar(): void {
    if (!this.preview) { this.error = 'Primero genere una vista previa del reporte.'; return; }
    this.exportando = true; this.error = '';
    const params = this.params().set('formato', this.formato);
    this.http.get(`${environment.apiUrlAuth}/gestion/reportes/${this.tipo}/descargar`, { params, responseType:'blob' }).subscribe({
      next: blob => {
        const url=URL.createObjectURL(blob); const a=document.createElement('a');
        a.href=url; a.download=`reporte-${this.tipo}.${this.formato}`; a.click(); URL.revokeObjectURL(url); this.exportando=false;
      },
      error: () => { this.error='No fue posible descargar el reporte.'; this.exportando=false; }
    });
  }

  cambioFiltro(): void { this.preview = undefined; }
  private validarRango(): boolean {
    if (!this.desde || !this.hasta || this.hasta < this.desde) { this.error='La fecha final debe ser igual o posterior a la inicial.'; return false; }
    return true;
  }
}
