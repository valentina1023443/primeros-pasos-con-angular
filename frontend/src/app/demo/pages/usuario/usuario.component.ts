import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UsuarioService } from './service/usuario.service';
import { Usuario } from 'src/app/models/usuario';
import { Cliente, ClienteService } from '../cliente/service/cliente.service';

@Component({
  selector: 'app-usuario',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './usuario.component.html',
  styleUrl: './usuario.component.scss'
})
export class UsuarioComponent implements OnInit {
  registros: Usuario[] = [];
  clientes: Cliente[] = [];
  form: FormGroup;
  editando = false;
  cargando = false;
  cargandoClientes = false;
  mensajeError = '';
  mensajeClientes = '';

  constructor(
    private service: UsuarioService,
    private clienteService: ClienteService,
    private modalService: NgbModal,
    private fb: FormBuilder
  ) {
    this.form = this.fb.group({
      id: [null],
      username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50), Validators.pattern(/^[A-Za-z0-9._-]+$/)]],
      password: ['', [Validators.required, Validators.minLength(10)]],
      rol: ['CLIENTE', Validators.required],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
      clienteId: [null],
      activo: [true],
    });
    this.form.get('rol')?.valueChanges.subscribe(() => this.configurarVinculoCliente());
  }

  ngOnInit(): void {
    this.cargar();
    this.cargarClientes();
  }

  cargar(): void {
    this.cargando = true;
    this.service.getUsuarios().subscribe({
      next: data => { this.registros = data; this.cargando = false; },
      error: () => { this.cargando = false; }
    });
  }

  cargarClientes(): void {
    this.cargandoClientes = true;
    this.clienteService.listar().subscribe({
      next: data => {
        this.clientes = (data || []).filter(cliente => cliente.activo !== false);
        this.mensajeClientes = this.clientes.length ? '' : 'Aún no hay clientes activos. Registre primero al cliente en Gestión de Clientes.';
        this.cargandoClientes = false;
      },
      error: () => {
        this.mensajeClientes = 'No se pudo cargar la lista de clientes. Verifique su sesión y vuelva a intentarlo.';
        this.cargandoClientes = false;
      }
    });
  }

  abrirModalAgregar(modal: any): void {
    this.editando = false;
    this.mensajeError = '';
    this.form.reset({ id: null, username: '', password: '', rol: 'CLIENTE', email: '', clienteId: null, activo: true });
    this.configurarValidadoresPassword();
    this.configurarVinculoCliente();
    this.modalService.open(modal);
  }

  abrirModalEditar(modal: any, item: Usuario): void {
    this.editando = true;
    this.mensajeError = '';
    this.form.patchValue({
      id: item.id,
      username: item.username,
      password: '',
      rol: this.normalizarRolUi(item.rol || ''),
      email: item.email,
      clienteId: item.clienteId ?? null,
      activo: item.activo,
    });
    this.configurarValidadoresPassword();
    this.configurarVinculoCliente();
    this.modalService.open(modal);
  }

  configurarVinculoCliente(): void {
    const clienteId = this.form.get('clienteId');
    if (!clienteId) return;
    if (this.form.get('rol')?.value === 'CLIENTE') {
      clienteId.setValidators([Validators.required]);
    } else {
      clienteId.clearValidators();
      clienteId.setValue(null, { emitEvent: false });
    }
    clienteId.updateValueAndValidity({ emitEvent: false });
  }

  guardar(modalRef: any): void {
    this.configurarVinculoCliente();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const values = { ...this.form.getRawValue(), username: String(this.form.value.username).trim(), email: String(this.form.value.email).trim() };
    const accion = this.editando ? this.service.actualizar(values) : this.service.guardar(values);
    accion.subscribe({
      next: () => { modalRef.close(); this.cargar(); },
      error: err => { this.mensajeError = err?.error?.message || 'Ocurrió un error al guardar la cuenta'; }
    });
  }

  nombreCliente(clienteId?: number | null): string {
    if (!clienteId) return '—';
    const cliente = this.clientes.find(item => item.id === clienteId);
    return cliente ? `${cliente.nombres} ${cliente.apellidos}` : `Cliente #${clienteId}`;
  }

  private configurarValidadoresPassword(): void {
    const password = this.form.get('password');
    if (!password) return;
    password.setValidators(this.editando ? [Validators.minLength(10)] : [Validators.required, Validators.minLength(10)]);
    password.updateValueAndValidity({ emitEvent: false });
  }

  private normalizarRolUi(rol: string): string {
    const normalized = rol.trim().toUpperCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[ -]/g, '_');
    if (normalized === 'ADMIN') return 'ADMINISTRADOR';
    if (normalized === 'RECEPCION') return 'SECRETARIA';
    if (normalized === 'VETERINARIO') return 'MEDICO';
    return normalized;
  }
}
