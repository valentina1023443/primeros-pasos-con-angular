# Parte de cliente (Angular) – Clínica Veterinaria

Solo se AGREGARON archivos nuevos. Los archivos originales no se modificaron, salvo dos líneas de conexión:
- `src/app/app-routing.module.ts` (4 rutas nuevas)
- `src/app/theme/layout/admin/navigation/navigation.ts` (4 opciones nuevas en el menú)

## Menú nuevo (crear, editar, eliminar, buscar)
Registro de Usuarios · Registro de Mascotas · Registro de Médicos · Citas

## Base de datos del cliente
`src/app/services/local-db.service.ts` guarda las tablas en el localStorage del navegador.

## Archivos nuevos
models/{mascota,medico,cita}.ts · services/local-db.service.ts · utils/{hash,alertas}.ts · demo/pages/cliente/*

## Ejecutar
npm install && npm start  →  http://localhost:4200
