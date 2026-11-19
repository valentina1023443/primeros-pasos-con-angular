# Clínica Veterinaria Uniminuto

Proyecto Full Stack para el examen final: Angular, API REST Spring Boot y MySQL 8.

## Inicio rápido

Requiere Docker Engine con Docker Compose v2:

```bash
docker compose up --build -d
```

- Aplicación: <http://localhost:4200>
- API y Swagger: <http://localhost:8080/clinica/v1/swagger-ui.html>
- Contrato OpenAPI: <http://localhost:8080/clinica/v1/v3/api-docs>

Cuentas ficticias de demostración: `admin.demo`, `medico.demo`, `secretaria.demo` y `despacho.demo`. La contraseña compartida es `DemoClinica2026!`. Para crear una cuenta de cliente, ingrese como administrador, registre o seleccione su ficha en Gestión de Clientes y luego use Gestión de Usuarios con el perfil Cliente. El cliente verá solo su ficha, sus mascotas y sus próximas citas. Las cuentas y la contraseña MySQL de Compose son solo para demostración local; no las use en producción.

## Documentación

- [Guía de instalación, uso y reglas operativas](docs/README-FINAL.md)
- [Ficha de entrega](docs/ENTREGA-EXAMEN.md)
- [Auditoría de requisitos del PDF](docs/auditoria-requerimientos.md)
- [API y ejemplos](docs/API.md)
- [Modelo de datos](docs/modelo-datos.md), [diagrama](docs/modelo-datos.png) y [SQL consolidado](docs/modelo-datos.sql)

El PDF original del examen se incluye en `docs/examen-final.pdf`. El ZIP contiene el código fuente, la configuración Docker Compose y la documentación técnica, pero **no incluye las evidencias funcionales**. El anexo `evidencias-funcionales-examen-final.pdf` se entrega como archivo separado junto con este ZIP.

La aplicación y las migraciones se probaron contra MySQL 8.0.46 local. Docker Compose no se pudo ejecutar en el entorno de prueba porque Docker Engine no estaba disponible; la configuración y los Dockerfile sí forman parte del proyecto.

## Acceso

La pantalla de inicio usa el logo de perro y gato de la aplicación, permite mostrar/ocultar la contraseña y recordar únicamente el nombre de usuario en el dispositivo. Si la API no responde, el login ahora lo distingue de una contraseña incorrecta. En una base Docker conservada de una versión anterior, las cuentas de demostración pueden no existir; reinicie el volumen solo si puede borrar sus datos. “¿Olvidaste tu contraseña?” y la solicitud de cuenta abren ayudas separadas; el administrador crea y vincula las cuentas de clientes. El restablecimiento automático por correo aún no está configurado.
