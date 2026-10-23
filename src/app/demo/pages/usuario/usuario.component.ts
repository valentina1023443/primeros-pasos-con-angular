import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UsuarioService } from './service/usuario.service';
import { Usuario } from 'src/app/models/usuario';

@Component({
  selector: 'app-usuario',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './usuario.component.html',
  styleUrl: './usuario.component.scss'
})
export class UsuarioComponent implements OnInit {

  registros: Usuario[] = [];
  form: FormGroup;
  editando = false;
  cargando = false;
  mensajeError = '';

  constructor(
    private service: UsuarioService,
    private modalService: NgbModal,
    private fb: FormBuilder
  ) {
    this.form = this.fb.group({
      id: [null],
      username: ['', Validators.required],
      password: [''],
      rol: ['CLIENTE', Validators.required],
      email: [''],
      activo: [true],
    });
  }

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.service.getUsuarios().subscribe({
      next: (data) => { this.registros = data; this.cargando = false; },
      error: () => { this.cargando = false; }
    });
  }

  abrirModalAgregar(modal: any): void {
    this.editando = false;
    this.form.reset({ rol: 'CLIENTE', activo: true });
    this.form.get('password')?.setValidators([Validators.required]);
    this.form.get('password')?.updateValueAndValidity();
    this.mensajeError = '';
    this.modalService.open(modal);
  }

  abrirModalEditar(modal: any, item: Usuario): void {
    this.editando = true;
    this.mensajeError = '';
    this.form.get('password')?.clearValidators();
    this.form.get('password')?.updateValueAndValidity();
    this.form.patchValue({
      id: item.id,
      username: item.username,
      rol: item.rol,
      email: item.email,
      activo: item.activo,
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
