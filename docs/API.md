# API REST — Clínica Veterinaria Uniminuto

Base URL local: `http://localhost:8080/clinica/v1`. Swagger UI y el esquema OpenAPI se publican en `/swagger-ui.html` y `/v3/api-docs` bajo ese prefijo.

## Sesión y permisos

Inicie sesión con `POST /auth/login` y JSON `{"username":"admin.demo","password":"DemoClinica2026!"}`. La respuesta contiene `accessToken`, `tokenType`, `expiresAt`, `userId` y `role`. El token opaco dura ocho horas y queda asociado a una sesión en la base de datos. Cierre la sesión con `POST /auth/logout` y el mismo encabezado.

Para rutas protegidas envíe `Authorization: Bearer <accessToken>`. El rol y el ID de usuario se derivan de la sesión validada en backend. No use `X-Role` o `X-User-Id` como mecanismo de autorización. Angular también filtra rutas y módulos por perfil, pero la barrera efectiva está en la API.

## Rutas principales

| Método | Ruta | Perfiles/uso |
|---|---|---|
| `POST` | `/auth/login` | Público; autentica y emite token |
| `POST` | `/auth/logout` | Usuario autenticado; invalida sesión |
| `GET` | `/portal-cliente/resumen` | Solo `CLIENTE`; devuelve únicamente la ficha vinculada, sus mascotas y próximas citas |
| `GET` | `/usuario/listar-ordenado` | Solo administrador; lista cuentas activas sin contraseñas y con `clienteId` cuando aplica |
| `POST` | `/usuario/guardar` | Solo administrador; crea cuenta. El perfil `CLIENTE` requiere `clienteId` y se vincula a una ficha activa |
| `POST` | `/usuario/actualizar` | Solo administrador; actualiza perfil/estado/vínculo; contraseña opcional en edición |
| `GET` | `/operaciones/inventario?texto=` | Administrador, médico y despacho: consulta productos, existencias y fechas |
| `POST` | `/operaciones/inventario` | Administrador: alta manual, lote y movimiento de entrada |
| `POST` | `/operaciones/inventario/importar/preview` | Administrador; `multipart/form-data`, campo `file` |
| `POST` | `/operaciones/inventario/importar/confirmar` | Administrador; confirma el ID de vista previa |
| `GET` | `/gestion/citas/calendario?desde=&hasta=&medicoId=` | Agenda persistida; las citas de médico se limitan a su sesión |
| `POST` / `PUT` | `/gestion/citas`, `/gestion/citas/{id}` | Crear o reprogramar; valida horario, disponibilidad, estado y solapamientos |
| `POST` | `/gestion/citas/{id}/cancelar` | Cancela con motivo y conserva historial |
| `POST` | `/gestion/atenciones/{citaId}` | Médico responsable o administrador; finaliza atención y agrega historia clínica |
| `POST` | `/gestion/formulas` | Médico responsable o administrador; prescribe sin descontar existencias |
| `GET` / `POST` / `PUT` | `/gestion/tarifas[/{id}]` | Consulta para administrador/secretaría; escritura de administrador |
| `POST` | `/gestion/prefacturas/{citaId}?servicio=` | Administrador/secretaría; calcula estimación y permite elegir servicio vigente |
| `GET` | `/gestion/documentos/{tipo}/{id}` | Descarga PDF de fórmula, prefactura, factura o despacho según tipo y permiso |
| `GET` | `/operaciones/despachos/pendientes` | Solo perfil `DESPACHO_MEDICO` |
| `GET` | `/operaciones/despachos/{formulaId}/lotes` | Solo despacho; lotes/vencimientos y prioridad FEFO |
| `PUT` | `/operaciones/despachos/{formulaId}/preparar` | Solo despacho; registra preparación y responsable |
| `POST` | `/operaciones/despachos/{formulaId}/confirmar` | Solo despacho; entrega total/parcial, transaccional e idempotente |
| `GET` | `/operaciones/despachos/historial` | Solo despacho; consulta entregas realizadas |
| `GET` | `/operaciones/despachos/{despachoId}/historial` | Solo despacho; bitácora de estado y responsables |
| `PUT` | `/operaciones/despachos/{formulaId}/cancelar` | Administrador; requiere motivo y no admite entregas iniciadas/confirmadas |
| `GET` | `/operaciones/facturas/citas-atendidas` | Administrador/secretaría; citas atendidas sin factura ni despacho pendiente |
| `GET` | `/operaciones/facturas?texto=` | Administrador/secretaría; búsqueda con pago neto y saldo |
| `POST` | `/operaciones/facturas` | Administrador/secretaría; tarifa específica opcional, medicamentos dispensados |
| `POST` | `/operaciones/facturas/{id}/pagos` | Administrador/secretaría; pago positivo, medio y clave idempotente |
| `GET` | `/operaciones/facturas/{id}/pagos` | Administrador/secretaría; historial de pagos y reversiones |
| `POST` | `/operaciones/facturas/{id}/pagos/{pagoId}/reversiones` | Solo administrador; reverso parcial/total con motivo y clave idempotente |
| `PUT` | `/operaciones/facturas/{id}/anular` | Administrador; solo con pago neto cero y motivo obligatorio |
| `GET` | `/gestion/reportes/{tipo}/preview?desde=&hasta=` | Administrador/secretaría; vista previa con filtros y totales |
| `GET` | `/gestion/reportes/{tipo}/descargar?desde=&hasta=&formato=pdf\|xlsx` | Administrador/secretaría; descarga de reportes |

`GET /operaciones/reportes/{tipo}?formato=pdf|xlsx` es la ruta heredada simple; para los reportes filtrables con vista previa use `/gestion/reportes`.

## Importación de inventario

El CSV/XLSX usa estas columnas en este orden: `codigo,nombre,tipo,lote,precioCompra,precioVenta,cantidad,stockMinimo,fechaIngreso,fechaVencimiento,registroInvima`. Las fechas usan `yyyy-MM-dd`. La respuesta de `preview` incluye `importacionId`, filas válidas, errores con fila/motivo y `puedeConfirmar`. Ejemplo de confirmación:

```json
{"importacionId":42}
```

La confirmación vuelve a leer los registros válidos guardados por el backend; el cliente no puede modificar el contenido validado enviando otro arreglo. Las filas rechazadas quedan en el log y no impiden confirmar las válidas.

## Fórmulas y despacho

La fórmula requiere cita atendida, historia clínica correspondiente y médico responsable. Incluye medicamento, dosis, frecuencia, duración, cantidad, vía e indicaciones; se pueden registrar varias fórmulas para una consulta. La fórmula no reduce stock.

Cuerpo de ejemplo para `POST /operaciones/despachos/{formulaId}/confirmar`:

```json
{
  "cantidad": 3,
  "operacionId": "a6c8c017-8fb3-4cc3-a12b-c35f6f0be2b4"
}
```

`cantidad` es la entrega de este intento y no puede exceder el saldo prescrito ni las existencias vigentes. `operacionId` debe ser única por intento lógico; repetir la misma clave devuelve el resultado previo sin descontar otra vez. El backend bloquea lotes con `SELECT ... FOR UPDATE`, aplica FEFO y registra los detalles y movimientos dentro de una transacción.

## Prefactura y factura

La prefactura calcula una estimación de servicio y medicamentos formulados. El parámetro `servicio` es opcional y por defecto usa `Consulta veterinaria`; puede indicarse, por ejemplo, como `POST /gestion/prefacturas/123?servicio=Cirugía%20demo`. La tarifa debe estar vigente y corresponder a la especialidad de la cita.

Ejemplo mínimo de factura:

```json
{"citaId":123}
```

Se puede seleccionar explícitamente una tarifa vigente aplicable:

```json
{"citaId":123,"tarifaId":4}
```

Si se omite `tarifaId`, el backend busca por defecto una tarifa vigente llamada `Consulta veterinaria` para la especialidad. La interfaz Angular también deja elegir entre las tarifas disponibles. La factura suma el servicio escogido y únicamente las cantidades efectivamente dispensadas; guarda precio e IVA por línea y una instantánea de la tarifa.

Un administrador puede autorizar un descuento con motivo:

```json
{"citaId":123,"tarifaId":4,"descuento":5000,"motivoDescuento":"Autorización administrativa"}
```

El descuento no puede ser negativo ni superar el subtotal. Un descuento mayor que cero requiere rol de administrador y motivo de hasta 500 caracteres. La factura se rechaza con HTTP 409 mientras exista una fórmula activa sin despacho o un despacho pendiente/parcial. Tras facturar, la API impide agregar fórmulas nuevas o iniciar despachos para la cita.

Ejemplo de pago: `{"valor":25000,"medio":"TRANSFERENCIA","operacionId":"pago-demo-001"}`. El valor debe ser positivo y no superar el saldo. Repetir la misma clave y contenido devuelve el resultado anterior; reutilizarla con otro contenido se rechaza.

Una reversión acepta, por ejemplo, `{"valor":5000,"medio":"TRANSFERENCIA","motivo":"Devolución autorizada","operacionId":"reembolso-demo-001"}`. El valor de solicitud es positivo y se registra como movimiento negativo enlazado al pago original; no puede superar el saldo aún reembolsable. Para anular use `PUT /operaciones/facturas/{id}/anular` con `{"motivo":"..."}` después de dejar el pago neto en cero. La aplicación conserva el historial financiero y de auditoría, pero no ejecuta transferencias ni devoluciones en una pasarela bancaria.

## Reportes filtrables

- Financieros: `financiero`, `facturas`, `ingresos-dia`, `ingresos-mes`, `ingresos-anio`, `ingresos-especialidad`, `ingresos-veterinario`, `ventas-medicamentos`.
- Inventario: `inventario`, `movimientos`.
- Gestión médica: `citas`, `citas-medico`, `medico`.

Los reportes aceptan `desde` y `hasta`; donde aplica, `medicoId` y `estado`. La descarga entrega PDF o XLSX con período, fecha de generación, filas y totales.

## Recuperación de contraseña

Esta versión no expone un endpoint de restablecimiento ni envía correos. La pantalla de login ofrece una ayuda que indica contactar al administrador, quien debe validar la identidad del usuario antes de cambiar su clave. No se permite restablecerla sin verificación.

## Cuentas de cliente

El alta de cuentas no es pública: el administrador crea primero la ficha en `/cliente/guardar` y luego crea el usuario en `/usuario/guardar`. El perfil se fuerza a `CLIENTE` en el servidor y el cliente debe estar activo y no tener otra cuenta asociada. Ejemplo del cuerpo de alta:

```json
{
  "username": "cliente.maria",
  "password": "ClaveInicialSegura2026!",
  "rol": "CLIENTE",
  "email": "maria@example.com",
  "clienteId": 2,
  "activo": true
}
```

Las contraseñas nuevas se almacenan con PBKDF2-HMAC-SHA256 y un mínimo de 10 caracteres. `GET /portal-cliente/resumen` no recibe un ID de cliente: lo obtiene del usuario asociado al token Bearer y devuelve su perfil, sus mascotas y las citas futuras no canceladas. Los endpoints heredados `/cliente/*`, `/mascota/*`, `/cita/*` y módulos administrativos no autorizan al rol `CLIENTE`.
