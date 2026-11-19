import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MedicamentoService, Medicamento } from './service/medicamento.service';
import { InventarioItem, InventarioService } from './service/inventario.service';

@Component({
  selector: 'app-medicamento',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './medicamento.component.html',
  styleUrl: './medicamento.component.scss'
})
export class MedicamentoComponent implements OnInit {

  registros: Medicamento[] = [];
  form: FormGroup;
  editando = false;
  cargando = false;
  mensajeError = '';
  inventario: InventarioItem[] = [];
  filtroInventario = '';
  importPreview: any = null;
  get esAdministrador(): boolean { return localStorage.getItem('role') === 'ADMINISTRADOR'; }

  constructor(
    private service: MedicamentoService,
    private inventarioService: InventarioService,
    private modalService: NgbModal,
    private fb: FormBuilder
  ) {
    this.form = this.fb.group({
      id: [null],
      nombre: ['', Validators.required],
      presentacion: [''],
      descripcion: [''],
    });
  }

  ngOnInit(): void {
    this.cargar();
    this.cargarInventario();
  }

  cargar(): void {
    this.cargando = true;
    this.service.listar().subscribe({
      next: (data) => { this.registros = data; this.cargando = false; },
      error: () => { this.cargando = false; }
    });
  }

  cargarInventario(): void {
    this.inventarioService.listar(this.filtroInventario).subscribe({ next: data => this.inventario = data, error: () => undefined });
  }

  previsualizarImportacion(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.inventarioService.preview(file).subscribe({
      next: data => this.importPreview = data,
      error: err => this.mensajeError = err?.error?.message || 'No se pudo validar el archivo'
    });
  }

  confirmarImportacion(): void {
    if (!this.importPreview?.puedeConfirmar || !this.importPreview?.importacionId) return;
    this.inventarioService.confirmar(this.importPreview.importacionId).subscribe({
      next: () => { this.importPreview = null; this.cargarInventario(); },
      error: err => this.mensajeError = err?.error?.message || 'No se pudo confirmar la importación'
    });
  }

  abrirModalAgregar(modal: any): void {
    this.editando = false;
    this.form.reset();
    this.mensajeError = '';
    this.modalService.open(modal);
  }

  abrirModalEditar(modal: any, item: Medicamento): void {
    this.editando = true;
    this.mensajeError = '';
    this.form.patchValue(item);
    this.modalService.open(modal);
  }

  guardar(modalRef: any): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const accion = this.editando
      ? this.service.actualizar(this.form.value)
      : this.service.guardar(this.form.value);

    accion.subscribe({
      next: () => { modalRef.close(); this.cargar(); },
      error: (err) => { this.mensajeError = err?.error?.message || 'Ocurrió un error al guardar'; }
    });
  }
}
