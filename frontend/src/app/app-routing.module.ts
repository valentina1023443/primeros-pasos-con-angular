import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { AdminComponent } from './theme/layout/admin/admin.component';
import { UsuarioComponent } from './demo/pages/usuario/usuario.component';
import { MascotaComponent } from './demo/pages/mascota/mascota.component'

import { RazaComponent } from './demo/pages/raza/raza.component';
import { ClienteComponent } from './demo/pages/cliente/cliente.component';
import { EspecializacionComponent } from './demo/pages/especializacion/especializacion.component';
import { MedicosComponent } from './demo/pages/medicos/medicos.component';
import { CitaComponent } from './demo/pages/cita/cita.component';
import { MedicamentoComponent } from './demo/pages/medicamento/medicamento.component';
import { FormulaMedicaComponent } from './demo/pages/formula-medica/formula-medica.component';
import { HistoriaMedicaComponent } from './demo/pages/historia-medica/historia-medica.component';
import { ReportesComponent } from './demo/pages/reportes/reportes.component';
import { DespachoComponent } from './demo/pages/despacho/despacho.component';
import { FacturacionComponent } from './demo/pages/facturacion/facturacion.component';
import { AccesoComponent } from './demo/pages/acceso/acceso.component';
import { PortalClienteComponent } from './demo/pages/portal-cliente/portal-cliente.component';
import { authGuard, roleGuard } from './services/auth.guard';

export const routes: Routes = [
  { path: 'acceso', component: AccesoComponent, data: { title: 'Iniciar sesión' } },
  {
    path: '',
    redirectTo: 'inicio',
    pathMatch: 'full'
  },  
  {
    path: 'inicio',
    component: AdminComponent,
    canActivate: [authGuard],
    data: { title: 'Inicio' },
    children: [      
      { path: 'usuarios', component: UsuarioComponent, canActivate: [roleGuard], data: { title: 'Usuarios', roles: ['ADMINISTRADOR'] }},
      { path: 'mascotas', component: MascotaComponent, canActivate: [roleGuard], data: { title: 'Mascotas', roles: ['ADMINISTRADOR','SECRETARIA','MEDICO','DESPACHO_MEDICO'] }},
      { path: 'razas', component: RazaComponent, canActivate: [roleGuard], data: { title: 'Razas', roles: ['ADMINISTRADOR'] } },
      { path: 'clientes', component: ClienteComponent, canActivate: [roleGuard], data: { title: 'Clientes', roles: ['ADMINISTRADOR','SECRETARIA'] } },
      { path: 'especializaciones', component: EspecializacionComponent, canActivate: [roleGuard], data: { title: 'Especializaciones', roles: ['ADMINISTRADOR'] } },
      { path: 'medicos', component: MedicosComponent, canActivate: [roleGuard], data: { title: 'Médicos', roles: ['ADMINISTRADOR','SECRETARIA'] } },
      { path: 'citas', component: CitaComponent, canActivate: [roleGuard], data: { title: 'Citas', roles: ['ADMINISTRADOR','SECRETARIA','MEDICO'] } },
      { path: 'medicamentos', component: MedicamentoComponent, canActivate: [roleGuard], data: { title: 'Medicamentos', roles: ['ADMINISTRADOR','DESPACHO_MEDICO'] } },
      { path: 'formulas-medicas', component: FormulaMedicaComponent, canActivate: [roleGuard], data: { title: 'Fórmulas médicas', roles: ['ADMINISTRADOR','MEDICO'] } },
      { path: 'historias-medicas', component: HistoriaMedicaComponent, canActivate: [roleGuard], data: { title: 'Historias clínicas', roles: ['ADMINISTRADOR','MEDICO'] } },
      { path: 'facturacion', component: FacturacionComponent, canActivate: [roleGuard], data: { title: 'Facturación y pagos', roles: ['ADMINISTRADOR','SECRETARIA'] }},
      { path: 'reportes', component: ReportesComponent, canActivate: [roleGuard], data: { title: 'Reportes', roles: ['ADMINISTRADOR','SECRETARIA'] }},
      { path: 'despacho', component: DespachoComponent, canActivate: [roleGuard], data: { title: 'Despacho y dispensación', roles: ['DESPACHO_MEDICO'] } },
      { path: 'mi-portal', component: PortalClienteComponent, canActivate: [roleGuard], data: { title: 'Mi portal', roles: ['CLIENTE'] } }
    ]
  },
  { path: '**', redirectTo: 'inicio' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
