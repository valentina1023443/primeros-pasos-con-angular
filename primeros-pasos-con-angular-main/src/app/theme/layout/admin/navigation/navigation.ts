export interface NavigationItem {
  id: string;
  title: string;
  type: 'item' | 'collapse' | 'group';
  translate?: string;
  icon?: string;
  hidden?: boolean;
  url?: string;
  classes?: string;
  exactMatch?: boolean;
  external?: boolean;
  target?: boolean;
  breadcrumbs?: boolean;

  children?: NavigationItem[];
}
export const NavigationItems: NavigationItem[] = [
  {
    id: 'navigation',
    title: 'Inicio',
    type: 'group',
    icon: 'icon-navigation',
    children: [
      {
        id: 'usuario',
        title: 'Gestión de Usuarios',
        type: 'item',
        url: '/inicio/usuarios',
        icon: 'feather icon-user',
        classes: 'nav-item'
      },
      /* ---------- Nuevos menus aqui -------------  */  
       {
        id: 'mascota',
        title: 'Gestión de Mascotas',
        type: 'item',
        url: '/inicio/mascotas',
        icon: 'feather icon-user',
        classes: 'nav-item'
      },
      {
        id: 'reg-usuarios',
        title: 'Registro de Usuarios',
        type: 'item',
        url: '/inicio/registro-usuarios',
        icon: 'feather icon-users',
        classes: 'nav-item'
      },
      {
        id: 'reg-mascotas',
        title: 'Registro de Mascotas',
        type: 'item',
        url: '/inicio/registro-mascotas',
        icon: 'feather icon-heart',
        classes: 'nav-item'
      },
      {
        id: 'reg-medicos',
        title: 'Registro de Médicos',
        type: 'item',
        url: '/inicio/registro-medicos',
        icon: 'feather icon-activity',
        classes: 'nav-item'
      },
      {
        id: 'reg-citas',
        title: 'Citas',
        type: 'item',
        url: '/inicio/registro-citas',
        icon: 'feather icon-calendar',
        classes: 'nav-item'
      },
    ]
  },  
];
