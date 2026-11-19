import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { PortalClienteService, PortalResumen } from './portal-cliente.service';

@Component({
  selector: 'app-portal-cliente',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './portal-cliente.component.html',
  styleUrl: './portal-cliente.component.scss'
})
export class PortalClienteComponent implements OnInit {
  resumen: PortalResumen | null = null;
  cargando = false;
  error = '';

  constructor(private readonly portal: PortalClienteService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.portal.resumen().subscribe({
      next: resumen => { this.resumen = resumen; this.cargando = false; },
      error: err => {
        this.error = err?.error?.message || 'No fue posible cargar la información de tu cuenta. Intenta de nuevo.';
        this.cargando = false;
      }
    });
  }

  fecha(value: string | Date): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
  }
}
