import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-acceso',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <main class="auth-shell">
      <div class="ambient ambient-top" aria-hidden="true"></div>
      <div class="ambient ambient-bottom" aria-hidden="true"></div>

      <section class="login-card" aria-labelledby="login-title">
        <a class="brand" href="#/acceso" aria-label="Clínica Veterinaria Uniminuto, inicio de sesión">
          <img src="assets/images/logo_mascotas.png" alt="" class="brand-logo">
          <span class="brand-name">Clínica Veterinaria<small>UNIMINUTO</small></span>
        </a>

        <div class="intro">
          <span class="eyebrow">PORTAL DE ACCESO</span>
          <h1 id="login-title">Bienvenido</h1>
          <p>Ingresa con la cuenta que te asignó la clínica.</p>
        </div>

        <form (ngSubmit)="ingresar()" #form="ngForm" novalidate>
          <div class="field-group">
            <label for="username">Usuario</label>
            <div class="input-wrap">
              <svg class="field-icon" viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="12" cy="8" r="3.5"></circle>
                <path d="M5 20c.5-3.5 3.1-5.5 7-5.5s6.5 2 7 5.5"></path>
              </svg>
              <input id="username" name="username" type="text" class="form-control"
                [(ngModel)]="username" autocomplete="username" autocapitalize="none"
                spellcheck="false" placeholder="Tu usuario" required>
            </div>
          </div>

          <div class="field-group password-group">
            <div class="password-heading">
              <label for="password">Contraseña</label>
              <button type="button" class="text-link" (click)="abrirRecuperacion()">
                ¿Olvidaste tu contraseña?
              </button>
            </div>
            <div class="input-wrap">
              <svg class="field-icon" viewBox="0 0 24 24" aria-hidden="true">
                <rect x="4.5" y="10" width="15" height="10" rx="2"></rect>
                <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v2.5"></path>
              </svg>
              <input id="password" name="password" [type]="mostrarPassword ? 'text' : 'password'"
                class="form-control password-input" [(ngModel)]="password"
                autocomplete="current-password" placeholder="Tu contraseña" required>
              <button type="button" class="visibility-toggle"
                [attr.aria-label]="mostrarPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'"
                [attr.aria-pressed]="mostrarPassword" (click)="alternarPassword()">
                <svg *ngIf="!mostrarPassword" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6Z"></path>
                  <circle cx="12" cy="12" r="2.7"></circle>
                </svg>
                <svg *ngIf="mostrarPassword" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="m3 3 18 18M10.6 6.2A10.7 10.7 0 0 1 12 6c6.1 0 9.5 6 9.5 6a15 15 0 0 1-3 3.6M6.1 6.8C3.7 8.2 2.5 12 2.5 12s3.4 6 9.5 6a9.8 9.8 0 0 0 3.2-.5"></path>
                  <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"></path>
                </svg>
              </button>
            </div>
          </div>

          <label class="remember-row" for="rememberUser">
            <input id="rememberUser" name="rememberUser" type="checkbox" [(ngModel)]="recordarUsuario">
            <span>Recordar mi usuario en este dispositivo</span>
          </label>

          <div class="alert alert-danger" *ngIf="error" role="alert" aria-live="polite">{{ error }}</div>

          <button class="submit-button" type="submit" [disabled]="cargando || form.invalid">
            <span class="button-spinner" *ngIf="cargando" aria-hidden="true"></span>
            {{ cargando ? 'Validando…' : 'Iniciar sesión' }}
            <svg *ngIf="!cargando" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6"></path></svg>
          </button>
        </form>

        <div class="security-note">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3 19 6v5c0 4.7-3 8-7 10-4-2-7-5.3-7-10V6l7-3Z"></path><path d="m9 12 2 2 4-4"></path></svg>
          <span>Acceso seguro para personal y clientes autorizados</span>
        </div>

        <footer class="login-footer">
          <span>¿Necesitas una cuenta?</span>
          <button type="button" class="text-link" (click)="abrirSolicitudAcceso()">Solicítala a la clínica</button>
        </footer>
      </section>

      <p class="page-footer">© {{ anioActual }} Clínica Veterinaria · Corporación Universitaria Minuto de Dios</p>

      <div class="modal-backdrop" *ngIf="mostrarAyuda" (click)="cerrarRecuperacion()">
        <section class="help-dialog" role="dialog" aria-modal="true" aria-labelledby="help-title" (click)="$event.stopPropagation()">
          <button type="button" class="dialog-close" aria-label="Cerrar" (click)="cerrarRecuperacion()">×</button>
          <div class="help-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"></circle><path d="M9.6 9a2.5 2.5 0 1 1 4.4 1.6c-.9 1-2 1.1-2 2.6M12 16.5v.1"></path></svg>
          </div>
          <h2 id="help-title">{{ tipoAyuda === 'recuperacion' ? 'Recupera tu acceso' : 'Solicita una cuenta' }}</h2>
          <p *ngIf="tipoAyuda === 'recuperacion'">La recuperación automática por correo no está habilitada en esta instalación. Para proteger tu cuenta, solicita al administrador de la clínica que restablezca tu contraseña.</p>
          <p *ngIf="tipoAyuda === 'cuenta'">El personal de la clínica debe crear tu ficha de cliente y asociarle una cuenta con el perfil Cliente. Después te entregará el usuario y la contraseña inicial.</p>
          <p class="help-note" *ngIf="tipoAyuda === 'recuperacion'">Nunca compartas tu contraseña actual. El administrador debe verificar tu identidad antes de ayudarte.</p>
          <p class="help-note" *ngIf="tipoAyuda === 'cuenta'">No envíes contraseñas por correo o chat. El acceso de cliente solo permite consultar la información vinculada a tu ficha.</p>
          <button type="button" class="submit-button dialog-button" (click)="cerrarRecuperacion()">Entendido, volver al acceso</button>
        </section>
      </div>
    </main>
  `,
  styles: [`
    :host{display:block;min-height:100vh;color:#172b3b;font-family:inherit}
    .auth-shell{position:relative;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;overflow:hidden;padding:36px 20px 64px;background:linear-gradient(135deg,#eef7f6 0%,#f7fafc 48%,#eef3fa 100%)}
    .ambient{position:absolute;border-radius:50%;pointer-events:none;filter:blur(1px)}
    .ambient-top{width:420px;height:420px;right:-150px;top:-190px;background:radial-gradient(circle,rgba(11,105,100,.12),rgba(11,105,100,0) 70%)}
    .ambient-bottom{width:540px;height:540px;left:-250px;bottom:-340px;background:radial-gradient(circle,rgba(57,111,171,.1),rgba(57,111,171,0) 70%)}
    .login-card{position:relative;z-index:1;width:min(100%,450px);padding:36px 40px 27px;background:rgba(255,255,255,.96);border:1px solid rgba(218,229,235,.9);border-radius:22px;box-shadow:0 24px 70px rgba(27,54,73,.13)}
    .brand{display:inline-flex;align-items:center;gap:12px;text-decoration:none;color:inherit;margin-bottom:34px}
    .brand-logo{width:58px;height:58px;object-fit:contain;border-radius:16px;background:#eff8f6}
    .brand-name{font-size:1rem;font-weight:750;line-height:1.2;letter-spacing:-.02em;color:#143c46}
    .brand-name small{display:block;margin-top:5px;color:#148078;font-size:.67rem;font-weight:800;letter-spacing:.14em}
    .intro{margin-bottom:25px}.eyebrow{display:block;margin-bottom:8px;color:#168078;font-size:.68rem;font-weight:800;letter-spacing:.15em}
    h1{margin:0;color:#183342;font-size:1.85rem;font-weight:750;letter-spacing:-.04em;line-height:1.2}
    .intro p{margin:8px 0 0;color:#6b7c87;font-size:.94rem}
    .field-group{margin-bottom:19px}.field-group label,.password-heading label{display:block;margin:0 0 8px;color:#304a56;font-size:.85rem;font-weight:700}
    .password-heading{display:flex;align-items:center;justify-content:space-between;gap:10px}
    .input-wrap{position:relative;display:flex;align-items:center}
    .form-control{width:100%;height:49px;padding:0 44px;border:1px solid #d7e2e7;border-radius:10px;background:#fbfdfd;color:#193844;font-size:.95rem;outline:none;transition:border-color .18s,box-shadow .18s,background .18s}
    .form-control::placeholder{color:#9aaab1}.form-control:focus{border-color:#148078;background:#fff;box-shadow:0 0 0 3px rgba(20,128,120,.13)}
    .field-icon{position:absolute;left:14px;width:19px;height:19px;fill:none;stroke:#8599a2;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round;pointer-events:none}
    .password-input{padding-right:48px}.visibility-toggle{position:absolute;right:7px;display:grid;width:36px;height:36px;place-items:center;border:0;border-radius:8px;background:transparent;color:#738993;cursor:pointer}
    .visibility-toggle:hover{background:#edf6f5;color:#08736c}.visibility-toggle:focus-visible,.text-link:focus-visible,.dialog-close:focus-visible{outline:3px solid rgba(20,128,120,.3);outline-offset:2px}
    .visibility-toggle svg{width:19px;height:19px;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}
    .text-link{padding:0;border:0;background:none;color:#08776f;font:inherit;font-size:.76rem;font-weight:700;text-decoration:none;cursor:pointer}
    .text-link:hover{color:#07534f;text-decoration:underline}.remember-row{display:flex;align-items:center;gap:9px;margin:2px 0 18px;color:#61747e;font-size:.8rem;cursor:pointer}
    .remember-row input{width:16px;height:16px;margin:0;accent-color:#08776f;cursor:pointer}
    .alert{margin:0 0 14px;padding:10px 12px;border:1px solid #f1c6c9;border-radius:9px;font-size:.84rem}
    .submit-button{display:flex;align-items:center;justify-content:center;gap:11px;width:100%;min-height:49px;padding:0 18px;border:0;border-radius:10px;background:linear-gradient(100deg,#08766f,#126b7a);box-shadow:0 8px 16px rgba(8,118,111,.18);color:#fff;font-size:.94rem;font-weight:700;cursor:pointer;transition:filter .15s,transform .15s,box-shadow .15s}
    .submit-button:hover:not(:disabled){filter:brightness(1.04);transform:translateY(-1px);box-shadow:0 11px 20px rgba(8,118,111,.22)}.submit-button:focus-visible{outline:3px solid rgba(8,118,111,.28);outline-offset:3px}.submit-button:disabled{opacity:.56;cursor:not-allowed}
    .submit-button svg{width:18px;height:18px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
    .button-spinner{width:17px;height:17px;border:2px solid rgba(255,255,255,.5);border-top-color:#fff;border-radius:50%;animation:spin .7s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
    .security-note{display:flex;align-items:center;justify-content:center;gap:7px;margin:22px 0 18px;color:#71848c;font-size:.74rem}.security-note svg{width:16px;height:16px;fill:none;stroke:#168078;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}
    .login-footer{display:flex;flex-wrap:wrap;justify-content:center;gap:5px;color:#829198;font-size:.75rem}
    .page-footer{position:absolute;z-index:1;bottom:17px;margin:0;padding:0 12px;color:#8a9aa0;font-size:.72rem;text-align:center}
    .modal-backdrop{position:fixed;z-index:5;inset:0;display:grid;place-items:center;padding:20px;background:rgba(15,35,45,.45);backdrop-filter:blur(5px)}
    .help-dialog{position:relative;width:min(100%,430px);padding:34px 32px 30px;border:1px solid #e3ecef;border-radius:18px;background:#fff;box-shadow:0 24px 70px rgba(15,35,45,.25);text-align:center;animation:appear .18s ease-out}
    @keyframes appear{from{opacity:0;transform:translateY(8px) scale(.985)}to{opacity:1;transform:translateY(0) scale(1)}}
    .dialog-close{position:absolute;top:12px;right:14px;width:34px;height:34px;border:0;border-radius:50%;background:#f2f6f6;color:#627780;font-size:1.5rem;line-height:1;cursor:pointer}
    .help-icon{display:grid;width:52px;height:52px;margin:0 auto 15px;place-items:center;border-radius:16px;background:#eaf6f4;color:#08776f}.help-icon svg{width:25px;height:25px;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}
    .help-dialog h2{margin:0 0 10px;color:#183342;font-size:1.35rem;font-weight:750}.help-dialog p{margin:0 0 12px;color:#60747e;font-size:.88rem;line-height:1.6}.help-dialog .help-note{padding:11px 12px;border-radius:9px;background:#f4f8f8;color:#70828a;font-size:.78rem}.dialog-button{margin-top:8px}
    @media(max-width:520px){.auth-shell{padding:22px 14px 58px}.login-card{padding:28px 23px 23px;border-radius:18px}.brand{margin-bottom:29px}.brand-logo{width:52px;height:52px}h1{font-size:1.65rem}.password-heading{align-items:flex-start}.text-link{font-size:.73rem}.page-footer{font-size:.66rem}}
    @media(prefers-reduced-motion:reduce){*,*::before,*::after{animation-duration:.01ms!important;animation-iteration-count:1!important;scroll-behavior:auto!important;transition-duration:.01ms!important}}
  `]
})
export class AccesoComponent implements OnInit {
  username = '';
  password = '';
  recordarUsuario = false;
  mostrarPassword = false;
  mostrarAyuda = false;
  tipoAyuda: 'recuperacion' | 'cuenta' = 'recuperacion';
  cargando = false;
  error = '';
  readonly anioActual = new Date().getFullYear();

  constructor(private readonly auth: AuthService, private readonly router: Router) {}

  ngOnInit(): void {
    const usuarioGuardado = localStorage.getItem('clinica.usuario-recordado');
    if (usuarioGuardado) {
      this.username = usuarioGuardado;
      this.recordarUsuario = true;
    }
  }

  alternarPassword(): void {
    this.mostrarPassword = !this.mostrarPassword;
  }

  abrirRecuperacion(): void {
    this.tipoAyuda = 'recuperacion';
    this.mostrarAyuda = true;
  }

  abrirSolicitudAcceso(): void {
    this.tipoAyuda = 'cuenta';
    this.mostrarAyuda = true;
  }

  cerrarRecuperacion(): void {
    this.mostrarAyuda = false;
  }

  ingresar(): void {
    if (this.cargando || !this.username.trim() || !this.password) return;
    this.cargando = true;
    this.error = '';
    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => {
        this.cargando = false;
        if (this.recordarUsuario) localStorage.setItem('clinica.usuario-recordado', this.username.trim());
        else localStorage.removeItem('clinica.usuario-recordado');
        this.router.navigateByUrl(localStorage.getItem('role') === 'CLIENTE' ? '/inicio/mi-portal' : '/inicio');
      },
      error: error => {
        this.cargando = false;
        if (error?.status === 0) {
          this.error = 'No se pudo conectar con el servidor. Verifica que Docker esté iniciado y ejecuta docker compose up -d desde la carpeta del proyecto.';
        } else if (error?.status === 401) {
          this.error = 'Usuario o contraseña incorrectos. Si conservas una base de datos Docker anterior, puede que no contenga las cuentas de demostración.';
        } else {
          this.error = error?.error?.message || 'No fue posible iniciar sesión. Inténtalo nuevamente o contacta al administrador.';
        }
      }
    });
  }
}
