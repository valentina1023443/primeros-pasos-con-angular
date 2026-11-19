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
