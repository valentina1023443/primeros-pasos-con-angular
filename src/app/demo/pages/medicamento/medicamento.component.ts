import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MedicamentoService, Medicamento } from './service/medicamento.service';

@Component({
  selector: 'app-medicamento',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './medicamento.component.html',
  styleUrl: './medicamento.component.scss'
})
export class MedicamentoComponent implements OnInit {

  registros: Medicamento[] = [];
  form: FormGroup;
  editando = false;
  cargando = false;
  mensajeError = '';

  constructor(
    private service: MedicamentoService,
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
  }

  cargar(): void {
    this.cargando = true;
    this.service.listar().subscribe({
      next: (data) => { this.registros = data; this.cargando = false; },
      error: () => { this.cargando = false; }
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
