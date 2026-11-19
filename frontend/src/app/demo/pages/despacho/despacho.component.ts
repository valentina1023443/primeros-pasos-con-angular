import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

@Component({ selector: 'app-despacho', standalone: true, imports: [CommonModule], templateUrl: './despacho.component.html', styleUrl: './despacho.component.scss' })
export class DespachoComponent implements OnInit {
  pendientes: any[]=[]; cargando=true; mensaje=''; preparando=0; lotesFormula: Record<number,any[]>={}; comprobanteDespachoId=0;
  constructor(private readonly http: HttpClient) {}
  ngOnInit(): void { this.cargar(); }
  cargar(): void { this.http.get<any[]>(`${environment.apiUrlAuth}/operaciones/despachos/pendientes`).subscribe({ next: d=>{this.pendientes=d;this.cargando=false;}, error:e=>{this.mensaje=e?.error?.message||'No se pudieron cargar las fórmulas';this.cargando=false;} }); }
  confirmar(item:any): void {
    const cantidad=Number(item.cantidadEntrega ?? item.cantidad_pendiente ?? item.cantidad_prescrita);
    if (!Number.isInteger(cantidad) || cantidad < 1) { this.mensaje='Ingrese una cantidad entera mayor que cero.'; return; }
    const operacionId=typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${item.formula_id}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    this.http.post<any>(`${environment.apiUrlAuth}/operaciones/despachos/${item.formula_id}/confirmar`,{cantidad,operacionId}).subscribe({next:r=>{this.mensaje=`Despacho ${r.estado.toLowerCase()}: ${r.cantidadEntregada} entregadas; ${r.cantidadPendiente} pendientes.`;this.comprobanteDespachoId=r.despachoId;this.cargar();},error:e=>this.mensaje=e?.error?.message||'No se pudo confirmar el despacho'});
  }
  preparar(item:any): void { this.preparando=item.formula_id;this.http.put<any>(`${environment.apiUrlAuth}/operaciones/despachos/${item.formula_id}/preparar`,{}).subscribe({next:r=>{item.despacho_id=r.despachoId;item.estado_despacho=r.estado;this.mensaje='Despacho en preparación.';this.preparando=0;},error:e=>{this.mensaje=e?.error?.message||'No se pudo iniciar la preparación';this.preparando=0;}}); }
  verLotes(item:any): void { if(this.lotesFormula[item.formula_id]){delete this.lotesFormula[item.formula_id];return;}this.http.get<any[]>(`${environment.apiUrlAuth}/operaciones/despachos/${item.formula_id}/lotes`).subscribe({next:rows=>this.lotesFormula[item.formula_id]=rows,error:e=>this.mensaje=e?.error?.message||'No fue posible consultar los lotes'}); }
  descargarComprobante(id:number): void { if(!id)return;this.http.get(`${environment.apiUrlAuth}/gestion/documentos/despacho/${id}`,{responseType:'blob'}).subscribe({next:blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`despacho-${id}.pdf`;a.click();URL.revokeObjectURL(url);},error:e=>this.mensaje=e?.error?.message||'No fue posible descargar el comprobante'}); }
}
