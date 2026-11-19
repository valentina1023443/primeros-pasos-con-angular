# Clínica Veterinaria Uniminuto — guía de instalación y uso

## Arquitectura

La entrega integra frontend Angular, API REST Spring Boot y base MySQL 8 mediante Docker Compose. Los módulos cubren inventario por lotes, importación CSV/XLSX con vista previa, citas y atención, historia clínica y fórmulas, despacho FEFO, prefacturación, facturación y pagos, y reportes PDF/XLSX.

La sesión usa un token Bearer temporal emitido por `/auth/login`. La identidad y los permisos se resuelven en el backend desde la sesión persistida. Los encabezados heredados de rol/usuario no constituyen prueba de autorización. Las rutas y el menú Angular se filtran por perfil, pero la API vuelve a validar cada operación. El rol `CLIENTE` solo puede consultar su portal privado; no puede usar rutas administrativas ni leer fichas de otros propietarios.

## Requisitos e inicio

Se requiere Docker Engine con Docker Compose v2 y los puertos locales 4200, 8080 y 3307 disponibles. Desde la raíz del proyecto:

```bash
docker compose up --build -d
docker compose ps
```

- Aplicación: <http://localhost:4200>
- API: <http://localhost:8080/clinica/v1>
- Swagger UI: <http://localhost:8080/clinica/v1/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/clinica/v1/v3/api-docs>
- MySQL desde el host: `localhost:3307`; el backend dentro de Compose usa `mysql:3306`.

Al iniciar por primera vez con un volumen vacío, Compose ejecuta en este orden `schema.sql`, `datos_prueba_parcial2.sql`, `migracion_base_adjunta.sql`, `migracion_examen_final.sql`, `migracion_requerimientos.sql` y `migracion_portal_cliente.sql`. Esos scripts no se vuelven a ejecutar automáticamente en un volumen ya inicializado. Si la base ya existía antes de esta versión, aplique una sola vez la migración nueva desde la raíz del proyecto:

```bash
docker compose cp backend/src/main/resources/migracion_portal_cliente.sql mysql:/tmp/migracion_portal_cliente.sql
docker compose exec mysql mysql -uroot -p1234 clinica -e "source /tmp/migracion_portal_cliente.sql"
```

`docker compose down -v` borra permanentemente los datos del volumen; úselo únicamente para reiniciar una base de demostración cuya información se puede eliminar.

## Cuentas ficticias

Las cuatro cuentas usan la contraseña común `DemoClinica2026!`. Son solo para demostración local y deben cambiarse o deshabilitarse antes de publicar la aplicación.

| Perfil | Usuario | Acceso general |
|---|---|---|
| Administrador | `admin.demo` | Gestión administrativa, inventario, tarifas, facturación y reportes |
| Médico | `medico.demo` | Citas propias, atención, historias y fórmulas |
| Secretaría | `secretaria.demo` | Recepción, agenda, prefacturación/facturación y reportes autorizados |
| Despacho médico | `despacho.demo` | Consulta de inventario/lotes y operaciones de despacho |

## Crear y habilitar una cuenta para un cliente

Las cuentas de cliente las crea el administrador; no hay registro público que permita escoger un rol o acceder a fichas ajenas. Primero cree la ficha del propietario desde **Gestión de Clientes** o elija una ficha existente. Después vaya a **Gestión de Usuarios → Agregar usuario**, seleccione el perfil **Cliente**, vincule la ficha, escriba usuario, correo y una contraseña inicial de al menos 10 caracteres y guarde. El servidor cifra la clave con PBKDF2; no la devuelve en las consultas de usuarios.

Al iniciar sesión, el cliente es enviado a **Mi portal** y solo recibe su nombre, ficha, mascotas asociadas y próximas citas. La API confirma la identidad desde el token Bearer, no desde un `clienteId` enviado por el navegador. Las rutas heredadas de clientes, mascotas y personal están bloqueadas para este perfil. Entregue la contraseña inicial por un canal seguro y pida al cliente cambiarla fuera del sistema; el cambio de clave se realiza desde Gestión de Usuarios.

## Inventario y carga masiva

El CSV/XLSX de carga usa estas columnas, en este orden: `codigo,nombre,tipo,lote,precioCompra,precioVenta,cantidad,stockMinimo,fechaIngreso,fechaVencimiento,registroInvima`. El código contiene ocho caracteres alfanuméricos; `tipo` es `MEDICAMENTO` o `UTENSILIO`; el registro Invima es obligatorio para medicamentos. Se validan duplicados, precios, cantidades, fechas y encabezados.

La vista previa separa filas válidas y rechazadas con fila y motivo. Se puede confirmar solo el conjunto válido aunque haya errores; la confirmación usa los datos validados guardados en el servidor y es transaccional. La importación conserva el usuario responsable y su resultado. La plantilla y los resultados de demostración no se incluyen en este ZIP; las evidencias funcionales se presentan en un PDF separado.

## Reglas operativas

- Prescribir no descuenta existencias. La confirmación del despacho descuenta por lote dentro de una transacción y registra movimientos; aplica FEFO, rechaza lotes vencidos, sobrantes y cantidades negativas, y admite entregas parciales idempotentes.
- El perfil `DESPACHO_MEDICO` consulta lotes, prepara y confirma entregas. Las entregas parciales o completas conservan sus detalles e historial y no se borran directamente.
- Las citas validan disponibilidad, estado y solapamientos de médico y mascota. La atención, historia y fórmulas quedan relacionadas; cancelar una cita deja bitácora de actor y motivo.
- La prefactura es una estimación del servicio escogido y los medicamentos formulados. La factura usa una tarifa vigente de la especialidad y solo incluye medicamentos efectivamente dispensados. Se permite elegir una tarifa específica desde la interfaz o mediante `tarifaId`; la tarifa de consulta es la opción por defecto de API.
- No se factura mientras haya fórmulas activas con despacho pendiente o parcial. Una vez facturada la atención, no se puede agregar otra fórmula ni realizar un nuevo despacho para esa cita.
- El descuento solo lo puede autorizar el administrador y requiere motivo. Pagos y reversiones guardan actor, medio y clave idempotente. La factura se puede anular únicamente cuando el neto pagado sea cero; no se eliminan movimientos. La aplicación registra la reversión, pero no transfiere dinero ni se conecta a una pasarela bancaria.
- Los reportes tienen vistas previas y filtros de período, con exportación PDF/XLSX; incluyen información financiera, inventario, movimientos, citas y médicos.

## Documentación y verificación

El contrato y ejemplos están en [API.md](API.md); el modelo relacional en [modelo-datos.md](modelo-datos.md), [modelo-datos.mmd](modelo-datos.mmd) y [modelo-datos.png](modelo-datos.png). La [auditoría frente al PDF](auditoria-requerimientos.md) cubre el alcance del proyecto. El informe de pruebas y las capturas de trabajo se excluyeron de este ZIP; el PDF de evidencias se entrega aparte.

Para compilar con JDK 21 y Node.js:

```bash
cd backend && bash mvnw test
bash mvnw -DskipTests package
cd ../frontend && npm ci && npm run build
```

En esta revisión, `mvnw test` terminó con 1 prueba aprobada y sin fallas; el empaquetado Maven y el build de Angular finalizaron correctamente. Angular reportó únicamente la advertencia de optimización por la dependencia CommonJS `sweetalert2`.

Los seis scripts de inicialización se ejecutaron contra MySQL 8.0.46; la migración de portal se probó además sobre la instancia local existente. La aplicación se probó desde navegador y API: el administrador creó una cuenta vinculada a una ficha, el cliente abrió su portal y las rutas administrativas/de otros clientes respondieron 403. Docker Compose como orquestador no se ejecutó en este entorno porque Docker Engine no estaba disponible; eso no reemplazó la prueba real de la API y Angular contra MySQL local.

## Solución de problemas

- Para ver logs de Compose: `docker compose logs -f mysql backend frontend`.
- Si aparece “Usuario o contraseña incorrectos” para `admin.demo`, confirme `DemoClinica2026!`. Una base Docker persistente conserva sus usuarios anteriores y no vuelve a cargar la semilla. Si es solo una base local de demostración y puede perder sus datos, `docker compose down -v` seguido de `docker compose up --build -d` la inicializa de nuevo con las cuentas demo. Si el error dice que no puede conectarse al servidor, primero levante Compose y espere a que MySQL esté saludable.
- Si una base ya fue inicializada, los scripts SQL no se repiten. Para pruebas limpias, reinicie el volumen solo si acepta perder sus datos.
- Dentro de Compose, el backend se conecta a MySQL por `mysql:3306`; el puerto `3307` es únicamente para acceso desde el host.

## Pantalla de acceso

La pantalla muestra el logo de la clínica, incluye un control accesible para mostrar/ocultar la contraseña, permite recordar solamente el usuario (nunca la contraseña) y distingue la ayuda para recuperar una cuenta existente de la solicitud de una cuenta nueva. La recuperación automática por correo no está habilitada: el usuario debe solicitar al administrador un restablecimiento tras verificar su identidad. Las capturas de estas funciones se incluyen en el PDF de evidencias, separado del ZIP.
