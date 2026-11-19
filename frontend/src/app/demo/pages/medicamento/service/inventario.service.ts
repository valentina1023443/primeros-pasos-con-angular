import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface InventarioItem { id: number; medicamento_id: number; codigo: string; tipo: string; registro_invima?: string; precio_compra: number; precio_venta: number; stock_minimo: number; nombre: string; presentacion?: string; stock: number; stock_vencido?: number; proximo_vencimiento?: string; }
@Injectable({ providedIn: 'root' })
export class InventarioService {
  private readonly base = `${environment.apiUrlAuth}/operaciones`;
  constructor(private readonly http: HttpClient) {}
  listar(texto = ''): Observable<InventarioItem[]> { return this.http.get<InventarioItem[]>(`${this.base}/inventario`, { params: new HttpParams().set('texto', texto) }); }
  preview(file: File, role = 'ADMINISTRADOR'): Observable<any> { const data = new FormData(); data.append('file', file); return this.http.post(`${this.base}/inventario/importar/preview`, data, { headers: new HttpHeaders({ 'X-Role': role }) }); }
  confirmar(importacionId: number, role = 'ADMINISTRADOR'): Observable<any> { return this.http.post(`${this.base}/inventario/importar/confirmar`, { importacionId }, { headers: new HttpHeaders({ 'Content-Type': 'application/json', 'X-Role': role }) }); }
}
