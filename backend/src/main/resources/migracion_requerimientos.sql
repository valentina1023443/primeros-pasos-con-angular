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
