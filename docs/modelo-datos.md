# Modelo de datos: relaciones y decisiones

El [diagrama Mermaid](modelo-datos.mmd) muestra las entidades y relaciones principales; la imagen es [modelo-datos.png](modelo-datos.png). El [SQL consolidado](modelo-datos.sql) reúne las migraciones canónicas 3–6; el esquema original y sus datos base se inicializan con `schema.sql` y `datos_prueba_parcial2.sql`.

## Entidades y relaciones principales

- **Usuarios y permisos:** `perfil` se relaciona con `usuario`; las cuentas de veterinarios se vinculan con `medico`, y cada cuenta con rol `CLIENTE` puede relacionarse con una única ficha de propietario mediante `usuario.cliente_id` (índice único y llave foránea). La sesión temporal identifica al usuario autenticado. El portal resuelve el cliente desde el token validado, no desde un ID suministrado por el navegador.
- **Agenda y atención:** `cliente` posee `mascota`; una mascota y un médico se relacionan con muchas `citas`. `horario_disponibilidad` define disponibilidad. Una cita puede generar una `historia_medica`, varias `formula_medica` y, como máximo, una `prefactura` y una `factura`. Los cambios de estado quedan en `cita_estado_log`.
- **Medicamentos e inventario:** `medicamento` identifica el producto clínico; `medicamento_inventario` contiene el código, tipo, Invima, precios, IVA, stock mínimo y estado. Cada código puede tener varios `lote_medicamento` con fecha de ingreso, vencimiento y cantidad. `movimiento_inventario` preserva las entradas y salidas por lote.
- **Prescripción y despacho:** cada `formula_medica` registra medicamento, dosis, frecuencia, duración, cantidad prescrita, vía e indicaciones, y se vincula con cita, historia, médico y mascota. Varias fórmulas pueden pertenecer a la misma cita. La relación única de `despacho_medicamento.formula_id` evita dos despachos para una fórmula; `despacho_detalle` registra lo entregado por lote. `despacho_log` y `despacho_operacion` guardan auditoría e idempotencia.
- **Servicios y finanzas:** `tarifa_servicio` configura precio e IVA por servicio/especialidad; `tarifa_log` audita cambios. `factura` tiene líneas en `factura_detalle`, una instantánea de tarifa en `factura_tarifa_snapshot` y un log. `pago_factura` conserva pagos y reversos como movimientos; cada reverso es negativo y referencia su pago original, con motivo, medio, actor y clave idempotente. El pago neto determina saldo y estado. `prefactura_detalle` conserva la estimación de servicio y medicamentos formulados.
- **Importaciones:** `importacion_inventario` conserva responsable, archivo y resultado; `importacion_inventario_detalle` guarda los datos válidos confirmables y `importacion_inventario_error` registra fila y motivo de rechazo.

## Restricciones y reglas relevantes

1. Las llaves foráneas vinculan persona, mascota, cita, atención, fórmula, despacho, lote, movimiento, factura y pagos.
2. `usuario.cliente_id` es único: una ficha activa puede tener como máximo una cuenta cliente. Solo el perfil `CLIENTE` puede mantener ese vínculo; el servicio valida el perfil y el cliente antes de guardar.
3. El código de inventario es único y tiene ocho caracteres. Invima es obligatorio para medicamentos y opcional para utensilios. Una misma carga no puede duplicar códigos.
4. La fecha de ingreso no puede ser futura; el lote debe vencer en o después de su ingreso y estar vigente para despacharse.
5. Las cantidades, precios e impuestos se validan en API. La salida se registra en la misma transacción que la actualización del lote y la confirmación del despacho.
6. El despacho prioriza FEFO, admite entrega parcial y no permite exceder la prescripción ni el stock disponible. Crear una fórmula no mueve existencias.
7. Las citas se validan contra disponibilidad y solapamientos; las cancelaciones guardan historial y actor.
8. La prefactura estima servicio y medicamentos formulados. La factura requiere cita atendida, tarifa vigente y ninguna fórmula con despacho pendiente/parcial. La factura carga solo medicamentos dispensados; una vez creada, se bloquean fórmulas y despachos nuevos para esa cita.
9. Los pagos y reversiones usan claves idempotentes. Un reverso no puede exceder el monto reembolsable del pago original. La factura se anula solo con pago neto cero; la anulación no borra movimientos y requiere autorización y motivo.
10. La aplicación registra la reversión para control contable y auditoría, pero no realiza transferencias ni devoluciones en un procesador externo; el desembolso se gestiona por el procedimiento financiero de la clínica.

## Scripts de inicialización

La base de demostración se instala en el orden definido en Docker Compose:

1. `schema.sql`: esquema base y tablas originales.
2. `datos_prueba_parcial2.sql`: datos base y cuentas de demostración.
3. `migracion_base_adjunta.sql`.
4. `migracion_examen_final.sql`.
5. `migracion_requerimientos.sql`.
6. `migracion_portal_cliente.sql`: asociación segura de una cuenta a una ficha y perfil/menú `Cliente`.

`docs/modelo-datos.sql` reúne las migraciones 3–6 y no contiene `DROP DATABASE`. Los scripts de migración son para una instalación base que aún no tenga esos cambios; la primera inicialización de un volumen vacío de Compose los ejecuta en el orden indicado. Si la base ya está inicializada, `migracion_portal_cliente.sql` se aplica una sola vez antes de reiniciar el backend.
