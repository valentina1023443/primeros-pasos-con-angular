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
        id: 'raza',
        title: 'Gestión de Razas',
        type: 'item',
        url: '/inicio/razas',
        icon: 'feather icon-tag',
        classes: 'nav-item'
      },
      {
        id: 'cliente',
        title: 'Gestión de Clientes',
        type: 'item',
        url: '/inicio/clientes',
        icon: 'feather icon-users',
        classes: 'nav-item'
      },
      {
        id: 'especializacion',
        title: 'Gestión de Especializaciones',
        type: 'item',
        url: '/inicio/especializaciones',
        icon: 'feather icon-award',
        classes: 'nav-item'
      },
      {
        id: 'medicos',
        title: 'Gestión de Médicos',
        type: 'item',
        url: '/inicio/medicos',
        icon: 'feather icon-user-check',
        classes: 'nav-item'
      },
      {
        id: 'cita',
        title: 'Gestión de Citas',
        type: 'item',
        url: '/inicio/citas',
        icon: 'feather icon-calendar',
        classes: 'nav-item'
      },
      {
        id: 'medicamento',
        title: 'Gestión de Medicamentos',
        type: 'item',
        url: '/inicio/medicamentos',
        icon: 'feather icon-package',
        classes: 'nav-item'
      },
      {
        id: 'formula-medica',
        title: 'Gestión de Fórmulas Médicas',
        type: 'item',
        url: '/inicio/formulas-medicas',
        icon: 'feather icon-file-text',
        classes: 'nav-item'
      },
      {
        id: 'historia-medica',
        title: 'Gestión de Historias Clínicas',
        type: 'item',
        url: '/inicio/historias-medicas',
        icon: 'feather icon-clipboard',
        classes: 'nav-item'
      },
      {
        id: 'despacho',
        title: 'Despacho y Dispensación',
        type: 'item',
        url: '/inicio/despacho',
        icon: 'feather icon-truck',
        classes: 'nav-item'
      },
      {
        id: 'facturacion',
        title: 'Facturación y pagos',
        type: 'item',
        url: '/inicio/facturacion',
        icon: 'feather icon-credit-card',
        classes: 'nav-item'
      },
      {
        id: 'reportes',
        title: 'Reportes administrativos',
        type: 'item',
        url: '/inicio/reportes',
        icon: 'feather icon-bar-chart-2',
        classes: 'nav-item'
      },
      {
        id: 'portal-cliente',
        title: 'Mi portal',
        type: 'item',
        url: '/inicio/mi-portal',
        icon: 'feather icon-home',
        classes: 'nav-item'
      },
    ]
  },  
];
