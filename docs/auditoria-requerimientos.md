# Auditoría final contra el PDF del examen

## Resultado

Se revisaron los requisitos del PDF frente al frontend Angular, la API Spring Boot, las migraciones MySQL, los permisos, la documentación y las pruebas funcionales realizadas. Se completaron las brechas detectadas en autenticación/autorización, importación auditable, vínculo de fórmulas, despacho parcial, facturación/reversiones, tarifas y reportes. Las evidencias visuales funcionales solicitadas por el examen se presentan como un PDF separado del ZIP del código.

## Matriz de cobertura

| Bloque del examen | Resultado en la versión revisada | Verificación |
|---|---|---|
| Inventario y carga masiva | Producto y utensilio; código de ocho caracteres; validación de tipo/Invima, precios, IVA, mínimos, fechas y lotes. CSV/XLSX con preview, errores por fila, confirmación transaccional de válidas y log de responsable. | Importación funcional de 60 filas por UI; un archivo mixto de 3 filas aceptó 1 y rechazó 2 con motivo. La evidencia visual está en el PDF aparte. |
| Citas y calendario | Citas persistidas, disponibilidad y solapamientos validados en API, cambios de estado y cancelaciones con actor/motivo/historial. Atenciones vinculan cita, mascota, médico e historia. | Se inspeccionó el calendario UI con datos de demostración; citas atendidas se usaron en pruebas de fórmula, prefactura y factura. |
| Atención y fórmulas | Diagnóstico obligatorio; fórmulas vinculadas con cita/historia/médico/mascota, dosis, frecuencia, duración, cantidad, vía e indicaciones. Una atención admite varias fórmulas; la prescripción no reduce existencias. | Se generaron y consultaron fórmulas asociadas a atenciones; se probó despacho parcial 2/5 y posterior completo; la fórmula queda bloqueada tras facturar. |
| Prefactura y factura | Prefactura PDF con servicio y medicamentos formulados; factura con tarifa vigente seleccionable y solo medicamentos realmente dispensados. Descuento administrativo con motivo, pagos/reversiones auditados y anulación al quedar pago neto en cero. | Prefactura con servicio explícito y PDF; UI generó factura con servicio de COP 80.000, IVA 0, descuento COP 5.000 y total COP 75.000; controles de despacho/facturación, pagos y reversos probados. |
| Reportes | Vistas previas con filtros y exportación PDF/XLSX para informes financieros, inventario y gestión clínica. | API generó reportes financieros PDF y XLSX con HTTP 200 y filtro por estado. La muestra está en el PDF de evidencias separado. |
| Despacho | Perfil dedicado `DESPACHO_MEDICO`, preparación, lotes FEFO, entregas totales/parciales, movimientos, idempotencia, historial y PDF. | Entrega 2/5 y luego completa; reintento idempotente sin doble descuento. En una prueba UI adicional se confirmó 1 unidad: stock de Meloxicam pasó de 95 a 94. |
| Seguridad y documentación | Sesión Bearer temporal; rol/usuario se derivan de sesión verificada; reglas en Angular y backend; Swagger/OpenAPI, modelo ER, SQL, guía y API. | Cuatro perfiles autenticaron. Operaciones financieras: admin/secretaría 200; médico/despacho 403; sin token 401; OpenAPI 200. |
| Cuentas y acceso de clientes | Alta de cuenta solo por administrador, vinculada a una ficha activa; perfil `CLIENTE` tiene portal propio y no accede a gestión de otros usuarios ni a rutas de propietarios. | UI: administrador creó una cuenta vinculada; cliente inició sesión y vio su ficha, mascota y cita; no aparecieron enlaces administrativos. API: resumen 200; `/cliente/*` y `/usuario/*` devolvieron 403. |

## Pruebas ejecutadas

- Se ejecutaron en MySQL 8.0.46, desde base limpia y en el orden de Compose, `schema.sql`, `datos_prueba_parcial2.sql`, `migracion_base_adjunta.sql`, `migracion_examen_final.sql`, `migracion_requerimientos.sql` y `migracion_portal_cliente.sql`. La instalación inicial dejó 38 tablas, cuatro inventarios demo, tres tarifas y cuatro cuentas de personal; el perfil Cliente quedó instalado. Se corrigió la sintaxis SQL para MySQL 8 y se añadió el backfill de fórmulas heredadas que coinciden con una cita atendida.
- Spring Boot inició conectado a esa base real y `/v3/api-docs` respondió 200. Los cuatro perfiles iniciaron sesión; las rutas protegidas devolvieron 401 sin token y 403 a los roles no autorizados. La consulta financiera devolvió 200 para administrador/secretaría y 403 para médico/despacho.
- La importación CSV se probó por navegador con 60/60 filas válidas. En una carga mixta se rechazaron dos filas (fecha de ingreso futura y columnas incompletas) y se confirmó solamente la fila válida.
- Se confirmó despacho FEFO parcial y completo, se verificaron existencias y movimiento, se repitió la clave idempotente sin doble descuento y se completó luego el despacho. En la prueba UI adicional, stock vigente observado 95 antes y 94 después de entregar una unidad. La factura devolvió HTTP 409 cuando el despacho estaba parcial; se permitió al completar/cancelar el pendiente. Después de facturar, agregar otra fórmula o iniciar un despacho para la cita devolvió 409.
- Desde la pantalla Angular se eligió la tarifa “Cirugía demo” de COP 80.000 con IVA 0 y se autorizó un descuento de COP 5.000 con motivo. La factura quedó en COP 75.000; la UI, la API y el PDF coinciden.
- En Gestión de Usuarios, el administrador creó una cuenta de prueba vinculada a la ficha activa de un propietario. La nueva cuenta pudo iniciar sesión y cargar Mi portal (HTTP 200 con solo su ficha, mascota y próximas citas); intentos contra las rutas de clientes y usuarios devolvieron 403. Chromium no registró errores de página/consola y la navegación del cliente no mostró el menú administrativo.
- Se probó concurrencia e idempotencia de pagos: de dos solicitudes concurrentes de COP 40.000 para la misma factura, una fue aceptada y la otra rechazada por validación transaccional; repetir una operación idéntica devolvió el resultado previo y reutilizar la clave con otro contenido devolvió 409. Los reversos se persistieron con montos negativos; un sobre-reembolso fue rechazado con 400 y la factura se anuló tras llegar a pago neto cero.
- Se generó una prefactura con el servicio “Cirugía demo” y se descargó el PDF. Los endpoints de reportes devolvieron PDF y XLSX válidos (HTTP 200); las salidas representativas se muestran en el PDF de evidencias independiente.
- La aplicación se usó en Chromium con login real; `mvnw test` pasó 1 prueba con cero errores; `mvnw -DskipTests package` y el build de Angular terminaron correctamente.

## Condiciones y límites

- Las cuentas, propietarios, mascotas, lotes e importaciones de prueba son datos ficticios; no se procesaron datos reales de clientes.
- El sistema registra el reverso financiero, pero **no se conecta a una pasarela ni ejecuta transferencias bancarias**. El reembolso externo debe realizarlo el operador según el procedimiento de la clínica y luego registrar el movimiento.
- Docker Compose como orquestador no se ejecutó aquí porque Docker Engine no estaba instalado. Sí se ejecutaron los scripts en orden en MySQL 8.0.46 y se probó la aplicación Angular/Spring Boot contra esa base local. La definición Compose y ambos Dockerfiles están incluidos en el ZIP.
- La prueba Maven existente es una prueba de carga de contexto (`ClinicaApplicationTests`), no una suite automatizada que cubra cada endpoint. Las operaciones funcionales y de concurrencia citadas se verificaron mediante llamadas reales a la API y navegador.

## Mejora de la pantalla de acceso

La pantalla presenta el logo, muestra/oculta la contraseña, recuerda el nombre de usuario sin guardar la clave y ofrece ayudas diferenciadas para recuperación y solicitud de cuenta. El flujo se verificó en Chromium en escritorio y móvil; no hubo errores de página ni desbordamiento horizontal. No hay envío de correo ni endpoint de restablecimiento automático; la interfaz lo informa y remite al administrador.
