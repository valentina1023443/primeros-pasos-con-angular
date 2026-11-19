USE clinica;

-- Base adjunta normalizada para MySQL 8. Se conservan los nombres que usa
-- el proyecto Java y se corrigen los comandos traducidos del archivo recibido.
CREATE TABLE IF NOT EXISTS perfil (
  perfil_id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(50) NOT NULL UNIQUE,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_modificacion DATETIME NULL
);

CREATE TABLE IF NOT EXISTS menu (
  menu_id INT AUTO_INCREMENT PRIMARY KEY,
  codigo VARCHAR(100) NOT NULL UNIQUE,
  titulo VARCHAR(255) NOT NULL,
  url TEXT NOT NULL,
  icono VARCHAR(255) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_modificacion DATETIME NULL
);

CREATE TABLE IF NOT EXISTS perfil_menu (
  perfil_menu_id INT AUTO_INCREMENT PRIMARY KEY,
  perfil_id INT NOT NULL,
  menu_id INT NOT NULL,
  UNIQUE KEY uq_perfil_menu (perfil_id, menu_id),
  CONSTRAINT fk_perfil_menu_perfil FOREIGN KEY (perfil_id) REFERENCES perfil(perfil_id),
  CONSTRAINT fk_perfil_menu_menu FOREIGN KEY (menu_id) REFERENCES menu(menu_id)
);

CREATE TABLE IF NOT EXISTS session (
  session_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token VARCHAR(500) NOT NULL,
  fecha_ini_sesion DATETIME NULL,
  fecha_expiracion DATETIME NULL,
  CONSTRAINT fk_session_usuario FOREIGN KEY (user_id) REFERENCES usuario(id)
);

CREATE TABLE IF NOT EXISTS token_otp (
  token_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  codigo VARCHAR(6) NOT NULL,
  fecha_generacion DATETIME NOT NULL,
  fecha_expiracion DATETIME NOT NULL,
  CONSTRAINT fk_otp_usuario FOREIGN KEY (user_id) REFERENCES usuario(id)
);

INSERT INTO perfil (perfil_id,nombre,activo,fecha_creacion) VALUES
  (1,'Administrador',TRUE,'2026-01-01 00:00:00'),
  (2,'Recepcion',TRUE,'2026-01-01 00:00:00'),
  (3,'Medico',TRUE,'2026-01-01 00:00:00'),
  (4,'Despacho medico',TRUE,'2026-01-01 00:00:00')
ON DUPLICATE KEY UPDATE nombre=VALUES(nombre), activo=VALUES(activo);

INSERT INTO menu (menu_id,codigo,titulo,url,icono,activo,fecha_creacion) VALUES
  (1,'cita','Gestión de citas','/inicio/citas','bi bi-calendar',TRUE,'2026-01-01 00:00:00'),
  (2,'clientes','Gestión de clientes','/inicio/clientes','bi bi-person-check',TRUE,'2026-01-01 00:00:00'),
  (3,'especializaciones','Gestión de Especializaciones','/inicio/especializaciones','fa-solid fa-user-graduado',TRUE,'2026-01-01 00:00:00'),
  (4,'formulas','Gestión de Fórmulas','/inicio/formulas','fa-solid fa-clipboard-list',TRUE,'2026-01-01 00:00:00'),
  (5,'historias','Gestión de Historias','/inicio/historias','fa-solid fa-book-medical',TRUE,'2026-01-01 00:00:00'),
  (6,'mascotas','Gestión de Mascotas','/inicio/mascotas','fa-solid fa-paw',TRUE,'2026-01-01 00:00:00'),
  (7,'medicamentos','Gestión de Medicamentos','/inicio/medicamentos','fa-solid fa-capsules',TRUE,'2026-01-01 00:00:00'),
  (8,'medicos','Gestión de Médicos','/inicio/medicos','fa-solid fa-user-doctor',TRUE,'2026-01-01 00:00:00'),
  (9,'usuarios','Gestión de Usuarios','/inicio/usuarios','feather icon-users',TRUE,'2026-01-01 00:00:00'),
  (10,'examen-final','Módulos del examen final','/inicio/examen-final','fa-solid fa-layer-group',TRUE,'2026-01-01 00:00:00')
ON DUPLICATE KEY UPDATE titulo=VALUES(titulo), url=VALUES(url), icono=VALUES(icono), activo=VALUES(activo);

INSERT IGNORE INTO perfil_menu (perfil_id,menu_id)
SELECT 1, menu_id FROM menu WHERE activo=TRUE;
INSERT IGNORE INTO perfil_menu (perfil_id,menu_id)
SELECT 2, menu_id FROM menu WHERE codigo IN ('cita','clientes','mascotas','formulas','historias','medicamentos','examen-final');
INSERT IGNORE INTO perfil_menu (perfil_id,menu_id)
SELECT 3, menu_id FROM menu WHERE codigo IN ('cita','mascotas','formulas','historias','medicamentos','examen-final');
INSERT IGNORE INTO perfil_menu (perfil_id,menu_id)
SELECT 4, menu_id FROM menu WHERE codigo IN ('medicamentos','formulas','examen-final');

-- El dump recibido trae rol como entero relacionado con perfil. El proyecto
-- existente usa texto para no romper sus servicios; se conserva ese contrato
-- y se agrega la relación opcional al perfil.
ALTER TABLE usuario ADD COLUMN perfil_id INT NULL;
UPDATE usuario SET perfil_id=1 WHERE perfil_id IS NULL AND LOWER(rol) IN ('administrador','admin');
UPDATE usuario SET perfil_id=2 WHERE perfil_id IS NULL AND LOWER(rol) IN ('secretaria','recepcion');
UPDATE usuario SET perfil_id=3 WHERE perfil_id IS NULL AND LOWER(rol) IN ('medico','veterinario');
UPDATE usuario SET perfil_id=4 WHERE perfil_id IS NULL AND LOWER(REPLACE(rol,' ','_')) IN ('despacho_medico','despacho_médico');
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_perfil FOREIGN KEY (perfil_id) REFERENCES perfil(perfil_id);
