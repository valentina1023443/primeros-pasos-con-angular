# Entrega del examen final — Clínica Veterinaria Uniminuto

## Alcance y cobertura

Se cotejó el proyecto con el PDF del examen y se completaron las brechas detectadas en autenticación/autorización, importación auditable, vínculo de fórmulas con atenciones, despacho parcial FEFO, facturación, reversiones y reportes. Además se habilitó administración de cuentas vinculadas a clientes y un portal de consulta limitado al propietario autenticado. El [PDF original del examen](examen-final.pdf) se conserva en la documentación.

Este ZIP contiene el código Angular y Spring Boot, configuración Docker Compose, scripts MySQL, contrato OpenAPI/Swagger, modelo ER y documentación técnica. **Las evidencias funcionales se excluyeron de este ZIP y se entregan en un anexo PDF separado:** `evidencias-funcionales-examen-final.pdf`.

## Entregables del proyecto

1. **Modelo de datos:** [diagrama ER PNG](modelo-datos.png), [fuente Mermaid](modelo-datos.mmd), [decisiones y restricciones](modelo-datos.md) y [SQL consolidado de migraciones](modelo-datos.sql). Las fuentes canónicas de Compose están en `backend/src/main/resources/`.
2. **Aplicación Full Stack:** código de `frontend/` y `backend/`, con configuración de ejecución por `docker-compose.yml` y Dockerfiles de frontend/backend.
3. **Carga masiva:** el sistema acepta CSV/XLSX, valida campos y presenta vista previa de filas válidas y rechazadas antes de confirmar. La demostración de 60 registros está en el PDF de evidencias separado.
4. **Citas y calendario:** agenda persistida con vistas y filtros; la demostración funcional está en el PDF de evidencias separado.
5. **Fórmulas, dispensación y facturación:** fórmulas vinculadas a atenciones, descuento de stock al dispensar con FEFO y registro de facturas; la demostración funcional está en el PDF de evidencias separado.
6. **Reportes:** generación de informes PDF y XLSX; la demostración funcional está en el PDF de evidencias separado.
7. **Documentación:** [guía de instalación](README-FINAL.md), [API y ejemplos](API.md), [auditoría de requisitos](auditoria-requerimientos.md) y [modelo de datos](modelo-datos.md).
8. **Cuentas de prueba ficticias:** las cuatro cuentas y su contraseña común están en la tabla del [README](README-FINAL.md); no son credenciales para producción.

## Funciones principales

| Bloque | Resultado implementado |
|---|---|
| Inventario | Productos y utensilios, códigos de 8 caracteres, validación Invima, compra/venta/IVA, lotes, vencimientos, mínimos, movimientos e importación CSV/XLSX con vista previa y log. |
| Citas y atención | Agenda persistida, disponibilidad y solapamientos, atención asociada a historia, cancelación con motivo e historial. |
| Fórmulas | Varias prescripciones por cita, cantidad, vía, dosis y stock consultable; no descuenta inventario al prescribir. |
| Despacho | Perfil dedicado, FEFO, preparación, entrega parcial, movimientos, idempotencia, bitácora y PDF. |
| Prefactura y factura | Prefactura estimada; factura con tarifa vigente seleccionable y medicamentos efectivamente dispensados; PDF, descuento administrativo con motivo, pagos, reversiones y anulación trazable. |
| Reportes | Vista previa filtrable y exportaciones PDF/XLSX financieras, de inventario y clínicas. |
| Seguridad y técnica | Sesión Bearer, roles verificados en backend, Spring Boot, Angular, MySQL, Compose, OpenAPI y documentación. |
| Acceso de clientes | El administrador asocia una cuenta `CLIENTE` a una sola ficha activa; el usuario ve únicamente su perfil, mascotas y próximas citas desde Mi portal. |

## Instalación y pruebas

Desde la raíz, ejecute `docker compose up --build -d`. Los puertos, scripts, credenciales ficticias y advertencias sobre el volumen están en el [README](README-FINAL.md). Swagger UI se sirve bajo `/clinica/v1/swagger-ui.html`.

El backend pasó `bash mvnw test` (1 prueba, sin fallas) y `bash mvnw -DskipTests package`; Angular pasó `npm run build -- --no-progress`. Las migraciones se ejecutaron en orden contra MySQL 8.0.46. Docker Compose como orquestador no se pudo levantar en este entorno porque Docker Engine no estaba instalado; el backend y Angular sí se ejecutaron contra MySQL local. Los detalles de las evidencias funcionales se presentan en el PDF separado, no en este ZIP.

Los datos de usuarios, propietarios, mascotas, lotes y operaciones de las demostraciones son ficticios. La aplicación registra reversiones contables, pero no realiza pagos ni reembolsos bancarios externos.

La creación administrativa de cuenta de cliente, su acceso al portal y el rechazo de rutas administrativas/ajenas se verificaron en navegador Chromium y API. Esos datos de ejecución son ficticios y no se incluyen en el ZIP.

## Pantalla de acceso

Se integró el logo de la aplicación, el ojo para mostrar/ocultar contraseña, recordar usuario sin guardar la clave, un diálogo de recuperación segura y una ayuda separada para solicitar una cuenta. La recuperación automática por correo no está configurada; la interfaz lo informa y remite al administrador. La demostración visual se encuentra en el anexo PDF separado.
