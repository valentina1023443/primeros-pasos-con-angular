-- SQL consolidado de las migraciones canónicas del proyecto.
-- Requiere schema.sql y datos_prueba_parcial2.sql ya aplicados.
-- Solo para instalación limpia/revisión; no recrea ni elimina la base.

-- ==================== migracion_base_adjunta.sql ====================

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

-- ==================== migracion_examen_final.sql ====================

USE clinica;

-- Extensión del parcial 2 para inventario, despacho, facturación y tarifas.
CREATE TABLE IF NOT EXISTS tarifa_servicio (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  especializacion_id BIGINT NULL,
  servicio VARCHAR(120) NOT NULL,
  valor DECIMAL(14,2) NOT NULL,
  iva_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 19,
  activa BOOLEAN NOT NULL DEFAULT TRUE,
  vigente_desde DATE NOT NULL,
  vigente_hasta DATE NULL,
  FOREIGN KEY (especializacion_id) REFERENCES especializacion(id)
);
CREATE TABLE IF NOT EXISTS tarifa_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tarifa_id BIGINT NOT NULL,
  valor_anterior DECIMAL(14,2),
  valor_nuevo DECIMAL(14,2) NOT NULL,
  usuario_id BIGINT NOT NULL,
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tarifa_id) REFERENCES tarifa_servicio(id),
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);
CREATE TABLE IF NOT EXISTS medicamento_inventario (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  medicamento_id BIGINT NOT NULL,
  codigo CHAR(8) NOT NULL UNIQUE,
  tipo ENUM('MEDICAMENTO','UTENSILIO') NOT NULL DEFAULT 'MEDICAMENTO',
  registro_invima VARCHAR(60) NULL,
  precio_compra DECIMAL(14,2) NOT NULL,
  precio_venta DECIMAL(14,2) NOT NULL,
  stock_minimo INT NOT NULL DEFAULT 0,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  FOREIGN KEY (medicamento_id) REFERENCES medicamento(medicamento_id)
);
CREATE TABLE IF NOT EXISTS lote_medicamento (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  inventario_id BIGINT NOT NULL,
  numero_lote VARCHAR(80) NOT NULL,
  fecha_ingreso DATE NOT NULL,
  fecha_vencimiento DATE NOT NULL,
  cantidad INT NOT NULL DEFAULT 0,
  UNIQUE KEY uq_inventario_lote (inventario_id, numero_lote),
  FOREIGN KEY (inventario_id) REFERENCES medicamento_inventario(id)
);
CREATE TABLE IF NOT EXISTS movimiento_inventario (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  inventario_id BIGINT NOT NULL,
  lote_id BIGINT NOT NULL,
  tipo ENUM('ENTRADA','SALIDA','AJUSTE','REVERSO') NOT NULL,
  cantidad INT NOT NULL,
  formula_id BIGINT NULL,
  usuario_id BIGINT NOT NULL,
  referencia VARCHAR(120),
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (inventario_id) REFERENCES medicamento_inventario(id),
  FOREIGN KEY (lote_id) REFERENCES lote_medicamento(id),
  FOREIGN KEY (formula_id) REFERENCES formula_medica(formula_id),
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);
CREATE TABLE IF NOT EXISTS despacho_medicamento (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  formula_id BIGINT NOT NULL UNIQUE,
  usuario_id BIGINT NOT NULL,
  estado ENUM('PENDIENTE_DESPACHO','EN_PREPARACION','DESPACHO_PARCIAL','DESPACHADO','PENDIENTE_DISPONIBILIDAD','CANCELADO') NOT NULL DEFAULT 'PENDIENTE_DESPACHO',
  entregado_en DATETIME NULL,
  FOREIGN KEY (formula_id) REFERENCES formula_medica(formula_id),
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);
CREATE TABLE IF NOT EXISTS despacho_detalle (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  despacho_id BIGINT NOT NULL,
  medicamento_id BIGINT NOT NULL,
  lote_id BIGINT NOT NULL,
  cantidad_prescrita INT NOT NULL,
  cantidad_entregada INT NOT NULL DEFAULT 0,
  FOREIGN KEY (despacho_id) REFERENCES despacho_medicamento(id),
  FOREIGN KEY (medicamento_id) REFERENCES medicamento_inventario(id),
  FOREIGN KEY (lote_id) REFERENCES lote_medicamento(id)
);
CREATE TABLE IF NOT EXISTS factura (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cita_id BIGINT NOT NULL UNIQUE,
  cliente_id BIGINT NOT NULL,
  mascota_id BIGINT NOT NULL,
  subtotal DECIMAL(14,2) NOT NULL,
  iva DECIMAL(14,2) NOT NULL,
  descuento DECIMAL(14,2) NOT NULL DEFAULT 0,
  total DECIMAL(14,2) NOT NULL,
  estado ENUM('PENDIENTE','PARCIAL','PAGADA','ANULADA') NOT NULL DEFAULT 'PENDIENTE',
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (cita_id) REFERENCES citas(id),
  FOREIGN KEY (cliente_id) REFERENCES cliente(cliente_id),
  FOREIGN KEY (mascota_id) REFERENCES mascota(mascota_id)
);
CREATE TABLE IF NOT EXISTS factura_detalle (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  factura_id BIGINT NOT NULL,
  concepto VARCHAR(160) NOT NULL,
  cantidad INT NOT NULL,
  precio_unitario DECIMAL(14,2) NOT NULL,
  iva_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 19,
  FOREIGN KEY (factura_id) REFERENCES factura(id)
);
CREATE TABLE IF NOT EXISTS pago_factura (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  factura_id BIGINT NOT NULL,
  valor DECIMAL(14,2) NOT NULL,
  medio VARCHAR(30) NOT NULL,
  usuario_id BIGINT NOT NULL,
  pagado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (factura_id) REFERENCES factura(id),
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);
-- El backend debe ejecutar la confirmación de despacho dentro de @Transactional,
-- bloquear lotes con SELECT ... FOR UPDATE, impedir vencidos/saldos negativos y
-- comprobar usuario.rol = 'DESPACHO_MEDICO' en cada operación sensible.

-- ==================== migracion_requerimientos.sql ====================

USE clinica;

ALTER TABLE medicamento_inventario ADD COLUMN fecha_proximo_pedido DATE NULL;
ALTER TABLE medicamento_inventario ADD COLUMN fecha_ingreso DATE NULL;
ALTER TABLE medicamento_inventario ADD COLUMN iva_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 19;
ALTER TABLE medico ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE usuario ADD COLUMN medico_id BIGINT NULL;
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_medico FOREIGN KEY (medico_id) REFERENCES medico(id);
UPDATE usuario SET medico_id=(SELECT id FROM medico ORDER BY id LIMIT 1) WHERE medico_id IS NULL AND UPPER(rol) IN ('MEDICO','VETERINARIO');
ALTER TABLE formula_medica ADD COLUMN cita_id BIGINT NULL;
ALTER TABLE formula_medica ADD COLUMN cantidad_prescrita INT NOT NULL DEFAULT 1;
ALTER TABLE formula_medica ADD COLUMN via_administracion VARCHAR(80) NULL;
ALTER TABLE formula_medica ADD CONSTRAINT fk_formula_cita FOREIGN KEY (cita_id) REFERENCES citas(id);
UPDATE formula_medica f JOIN citas c ON c.mascota_id=f.mascota_id AND c.medico_id=f.medico_id AND DATE(c.fecha)=DATE(f.fecha_emision) AND c.estado='ATENDIDA' SET f.cita_id=c.id WHERE f.cita_id IS NULL;
UPDATE formula_medica f JOIN historias_medicas h ON h.cita_id=f.cita_id AND h.mascota_id=f.mascota_id SET f.historia_medica_id=h.id WHERE f.cita_id IS NOT NULL AND f.historia_medica_id IS NULL;
ALTER TABLE movimiento_inventario ADD COLUMN despacho_id BIGINT NULL;
ALTER TABLE movimiento_inventario ADD CONSTRAINT fk_movimiento_despacho FOREIGN KEY (despacho_id) REFERENCES despacho_medicamento(id);
ALTER TABLE pago_factura ADD COLUMN operacion_id VARCHAR(80) NULL;
CREATE UNIQUE INDEX uq_pago_operacion ON pago_factura(operacion_id);
ALTER TABLE pago_factura ADD COLUMN tipo ENUM('PAGO','REVERSO') NOT NULL DEFAULT 'PAGO';
ALTER TABLE pago_factura ADD COLUMN pago_original_id BIGINT NULL;
ALTER TABLE pago_factura ADD COLUMN motivo_reversion VARCHAR(500) NULL;
ALTER TABLE pago_factura ADD CONSTRAINT fk_pago_original FOREIGN KEY (pago_original_id) REFERENCES pago_factura(id);
ALTER TABLE citas ADD COLUMN fecha_fin DATETIME NULL;
ALTER TABLE citas ADD COLUMN cancelada_en DATETIME NULL;
ALTER TABLE citas ADD COLUMN cancelada_por BIGINT NULL;
ALTER TABLE citas ADD COLUMN motivo_cancelacion VARCHAR(255) NULL;

CREATE TABLE IF NOT EXISTS horario_disponibilidad (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  medico_id BIGINT NOT NULL,
  dia_semana TINYINT NOT NULL,
  hora_inicio TIME NOT NULL,
  hora_fin TIME NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  FOREIGN KEY (medico_id) REFERENCES medico(id),
  UNIQUE KEY uq_horario_medico (medico_id, dia_semana, hora_inicio, hora_fin)
);
CREATE TABLE IF NOT EXISTS cita_estado_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cita_id BIGINT NOT NULL,
  estado_anterior VARCHAR(30) NULL,
  estado_nuevo VARCHAR(30) NOT NULL,
  usuario_id BIGINT NOT NULL,
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  motivo VARCHAR(255) NULL,
  FOREIGN KEY (cita_id) REFERENCES citas(id),
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);
CREATE TABLE IF NOT EXISTS importacion_inventario (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nombre_archivo VARCHAR(255) NOT NULL,
  usuario_id BIGINT NULL,
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  total_filas INT NOT NULL,
  filas_validas INT NOT NULL,
  filas_error INT NOT NULL,
  estado ENUM('PREVIA','CONFIRMADA','CANCELADA') NOT NULL DEFAULT 'PREVIA',
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);
CREATE TABLE IF NOT EXISTS importacion_inventario_error (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  importacion_id BIGINT NOT NULL,
  fila INT NOT NULL,
  motivo VARCHAR(500) NOT NULL,
  datos JSON NULL,
  FOREIGN KEY (importacion_id) REFERENCES importacion_inventario(id)
);
CREATE TABLE IF NOT EXISTS importacion_inventario_detalle (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  importacion_id BIGINT NOT NULL,
  fila INT NOT NULL,
  datos JSON NOT NULL,
  UNIQUE KEY uq_importacion_fila (importacion_id, fila),
  FOREIGN KEY (importacion_id) REFERENCES importacion_inventario(id)
);
CREATE TABLE IF NOT EXISTS despacho_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  despacho_id BIGINT NOT NULL,
  estado_anterior VARCHAR(40),
  estado_nuevo VARCHAR(40) NOT NULL,
  usuario_id BIGINT NOT NULL,
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  observacion VARCHAR(500),
  FOREIGN KEY (despacho_id) REFERENCES despacho_medicamento(id),
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);
CREATE TABLE IF NOT EXISTS despacho_operacion (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  despacho_id BIGINT NOT NULL,
  operacion_id VARCHAR(80) NOT NULL,
  usuario_id BIGINT NOT NULL,
  cantidad INT NOT NULL,
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_despacho_operacion (despacho_id, operacion_id),
  FOREIGN KEY (despacho_id) REFERENCES despacho_medicamento(id),
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);
CREATE TABLE IF NOT EXISTS factura_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  factura_id BIGINT NOT NULL,
  accion VARCHAR(50) NOT NULL,
  usuario_id BIGINT NOT NULL,
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  detalle VARCHAR(500),
  FOREIGN KEY (factura_id) REFERENCES factura(id),
  FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);
CREATE TABLE IF NOT EXISTS factura_tarifa_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  factura_id BIGINT NOT NULL,
  servicio VARCHAR(120) NOT NULL,
  tarifa DECIMAL(14,2) NOT NULL,
  iva_porcentaje DECIMAL(5,2) NOT NULL,
  FOREIGN KEY (factura_id) REFERENCES factura(id)
);
CREATE TABLE IF NOT EXISTS prefactura (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cita_id BIGINT NOT NULL UNIQUE,
  subtotal DECIMAL(14,2) NOT NULL,
  iva DECIMAL(14,2) NOT NULL,
  descuento DECIMAL(14,2) NOT NULL DEFAULT 0,
  total DECIMAL(14,2) NOT NULL,
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (cita_id) REFERENCES citas(id)
);
CREATE TABLE IF NOT EXISTS prefactura_detalle (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  prefactura_id BIGINT NOT NULL,
  concepto VARCHAR(180) NOT NULL,
  cantidad INT NOT NULL,
  precio_unitario DECIMAL(14,2) NOT NULL,
  iva_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 19,
  FOREIGN KEY (prefactura_id) REFERENCES prefactura(id)
);

CREATE INDEX idx_movimiento_inventario_fecha ON movimiento_inventario(fecha);
CREATE INDEX idx_cita_fecha_fin ON citas(medico_id, fecha, fecha_fin);
CREATE INDEX idx_factura_fecha_estado ON factura(fecha, estado);

-- Datos iniciales útiles para ejecutar el flujo completo en la instalación de examen.
INSERT IGNORE INTO medicamento_inventario(medicamento_id,codigo,tipo,registro_invima,precio_compra,precio_venta,stock_minimo)
SELECT medicamento_id,'MED00001','MEDICAMENTO','INVIMA-DEMO-001',800,1500,10 FROM medicamento WHERE LOWER(nombre)='amoxicilina';
INSERT IGNORE INTO medicamento_inventario(medicamento_id,codigo,tipo,registro_invima,precio_compra,precio_venta,stock_minimo)
SELECT medicamento_id,'MED00002','MEDICAMENTO','INVIMA-DEMO-002',1200,2500,8 FROM medicamento WHERE LOWER(nombre)='meloxicam';
INSERT IGNORE INTO medicamento_inventario(medicamento_id,codigo,tipo,registro_invima,precio_compra,precio_venta,stock_minimo)
SELECT medicamento_id,'MED00003','MEDICAMENTO','INVIMA-DEMO-003',600,1100,8 FROM medicamento WHERE LOWER(nombre)='ivermectina';
INSERT IGNORE INTO medicamento_inventario(medicamento_id,codigo,tipo,registro_invima,precio_compra,precio_venta,stock_minimo)
SELECT medicamento_id,'MED00004','MEDICAMENTO','INVIMA-DEMO-004',500,1000,8 FROM medicamento WHERE LOWER(nombre)='cetirizina';
INSERT IGNORE INTO lote_medicamento(inventario_id,numero_lote,fecha_ingreso,fecha_vencimiento,cantidad)
SELECT id,'LOTE-DEMO-2026',CURDATE(),DATE_ADD(CURDATE(),INTERVAL 2 YEAR),100 FROM medicamento_inventario WHERE codigo IN ('MED00001','MED00002','MED00003','MED00004');
INSERT INTO movimiento_inventario(inventario_id,lote_id,tipo,cantidad,usuario_id,referencia)
SELECT i.id,l.id,'ENTRADA',100,u.id,'CARGA_INICIAL_DEMO' FROM medicamento_inventario i JOIN lote_medicamento l ON l.inventario_id=i.id AND l.numero_lote='LOTE-DEMO-2026' JOIN usuario u ON u.username='admin.demo' WHERE i.codigo IN ('MED00001','MED00002','MED00003','MED00004') AND NOT EXISTS (SELECT 1 FROM movimiento_inventario x WHERE x.lote_id=l.id AND x.referencia='CARGA_INICIAL_DEMO');
INSERT IGNORE INTO horario_disponibilidad(medico_id,dia_semana,hora_inicio,hora_fin,activo)
SELECT m.id,d.dia,'08:00:00','17:00:00',TRUE FROM medico m CROSS JOIN (SELECT 1 dia UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) d;
INSERT INTO tarifa_servicio(especializacion_id,servicio,valor,iva_porcentaje,vigente_desde,activa)
SELECT e.id,'Consulta veterinaria',50000,19,CURDATE(),TRUE FROM especializacion e WHERE NOT EXISTS (SELECT 1 FROM tarifa_servicio t WHERE t.especializacion_id=e.id AND t.servicio='Consulta veterinaria' AND t.activa=1);

-- ==================== migracion_portal_cliente.sql ====================

USE clinica;

-- Una ficha de propietario puede tener como máximo una cuenta de acceso.
ALTER TABLE usuario ADD COLUMN cliente_id BIGINT NULL;
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(cliente_id);
CREATE UNIQUE INDEX uq_usuario_cliente ON usuario(cliente_id);

INSERT INTO perfil (nombre, activo, fecha_creacion)
VALUES ('Cliente', TRUE, NOW())
ON DUPLICATE KEY UPDATE activo=TRUE;

INSERT INTO menu (codigo, titulo, url, icono, activo, fecha_creacion)
VALUES ('portal-cliente', 'Mi portal', '/inicio/mi-portal', 'feather icon-home', TRUE, NOW())
ON DUPLICATE KEY UPDATE titulo=VALUES(titulo), url=VALUES(url), icono=VALUES(icono), activo=TRUE;

INSERT IGNORE INTO perfil_menu (perfil_id, menu_id)
SELECT p.perfil_id, m.menu_id FROM perfil p CROSS JOIN menu m
WHERE p.nombre='Cliente' AND m.codigo='portal-cliente';
