import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { AdminComponent } from './theme/layout/admin/admin.component';
import { UsuarioComponent } from './demo/pages/usuario/usuario.component';
import { MascotaComponent } from './demo/pages/mascota/mascota.component'
import { UsuariosClienteComponent } from './demo/pages/cliente/usuarios/usuarios-cliente.component';
import { MascotasClienteComponent } from './demo/pages/cliente/mascotas/mascotas-cliente.component';
import { MedicosClienteComponent } from './demo/pages/cliente/medicos/medicos-cliente.component';
import { CitasClienteComponent } from './demo/pages/cliente/citas/citas-cliente.component';


export const routes: Routes = [
  {
    path: '',
    redirectTo: 'inicio',
    pathMatch: 'full'
  },  
  {
    path: 'inicio',
    component: AdminComponent,
    data: { title: 'Inicio' },
    children: [      
      { path: 'usuarios', component: UsuarioComponent, data: { title: 'Usuarios' }},
      { path: 'mascotas', component: MascotaComponent, data: { title: 'Mascotas' }},
      // Parte de cliente con base de datos
      { path: 'registro-usuarios', component: UsuariosClienteComponent, data: { title: 'Registro de usuarios' }},
      { path: 'registro-mascotas', component: MascotasClienteComponent, data: { title: 'Registro de mascotas' }},
      { path: 'registro-medicos', component: MedicosClienteComponent, data: { title: 'Registro de médicos' }},
      { path: 'registro-citas', component: CitasClienteComponent, data: { title: 'Citas' }}
     
    ]
  },
  { path: '**', redirectTo: 'inicio' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
