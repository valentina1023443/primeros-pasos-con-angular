import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BackendService } from 'src/app/services/backend.service';
import { environment } from 'src/environments/environment';

export interface PortalResumen {
  cliente: {
    id: number;
    tipoDocumento: string;
    numeroDocumento: string;
    nombres: string;
    apellidos: string;
    telefono?: string | null;
    direccion?: string | null;
  };
  mascotas: Array<{ id: number; nombre: string; edad?: number | null; raza?: string | null; especie?: string | null }>;
  proximasCitas: Array<{ id: number; fecha: string | Date; motivo: string; estado: string; mascota: string; medico?: string | null }>;
}

@Injectable({ providedIn: 'root' })
export class PortalClienteService {
  constructor(private readonly backend: BackendService) {}

  resumen(): Observable<PortalResumen> {
    return this.backend.get<PortalResumen>(environment.apiUrlAuth, 'portal-cliente', 'resumen');
  }
}
