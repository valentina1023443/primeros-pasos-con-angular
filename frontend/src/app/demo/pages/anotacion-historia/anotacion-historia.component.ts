import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AnotacionHistoriaService, AnotacionHistoria } from './service/anotacion-historia.service';

@Component({
  selector: 'app-anotacion-historia',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './anotacion-historia.component.html',
  styleUrl: './anotacion-historia.component.scss'
})
export class AnotacionHistoriaComponent implements OnInit {

  registros: AnotacionHistoria[] = [];
  form: FormGroup;
  editando = false;
  cargando = false;
  mensajeError = '';

  constructor(
    private service: AnotacionHistoriaService,
    private modalService: NgbModal,
    private fb: FormBuilder
  ) {
    this.form = this.fb.group({
      id: [null],
      historiaMedicaId: [null, Validators.required],
      descripcion: ['', Validators.required],
      fecha: ['', Validators.required],
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

  abrirModalEditar(modal: any, item: AnotacionHistoria): void {
    this.editando = true;
    this.mensajeError = '';
    this.form.patchValue({
      id: item.id,
      historiaMedicaId: item.historiaMedica?.id,
      descripcion: item.descripcion,
      fecha: item.fecha?.substring(0, 16),
    });
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
