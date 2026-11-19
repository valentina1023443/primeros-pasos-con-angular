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
