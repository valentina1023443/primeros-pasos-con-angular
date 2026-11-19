# Swagger / OpenAPI

El backend incluye `springdoc-openapi-starter-webmvc-ui` 3.0.x, compatible con Spring Boot 4.0.x según la [matriz oficial de compatibilidad](https://springdoc.org/faq.html#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot), y configuración OpenAPI en `com.uniminuto.clinica.config.OpenApiConfig`.

Con `docker compose up --build -d`:

- Swagger UI: `http://localhost:8080/clinica/v1/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/clinica/v1/v3/api-docs`

Los endpoints protegidos usan el esquema HTTP Bearer con el token opaco que devuelve `POST /clinica/v1/auth/login`. El inicio de sesión está marcado como público. Los headers internos `X-Role` y `X-User-Id` se ocultan del esquema y se calculan desde la sesión validada; no deben inventarse ni enviarse como credenciales.

La tabla de endpoints, la matriz de perfiles, los cuerpos de importación/dispensación y ejemplos se mantienen en [`API.md`](API.md). Las rutas bajo `gestion/reportes` tienen previsualización, rango de fechas y descarga `pdf` o `xlsx`; la API de facturación incluye consulta de pagos y reversos auditados, además de las reglas para anular una factura con pago neto cero.
