package com.uniminuto.clinica.apicontroller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/portal-cliente")
@CrossOrigin(origins = "*", allowedHeaders = {"Authorization", "Content-Type"})
public class PortalClienteController {
    private final JdbcTemplate db;

    public PortalClienteController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/resumen")
    public Map<String, Object> resumen(@RequestAttribute(BearerSessionFilter.USER_ID) Long userId) {
        List<Map<String, Object>> vinculacion = db.queryForList(
                "SELECT cliente_id FROM usuario WHERE id=? AND UPPER(rol)='CLIENTE' AND activo=1", userId);
        if (vinculacion.isEmpty() || vinculacion.get(0).get("cliente_id") == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La cuenta no está asociada a una ficha de cliente activa. Contacte a la clínica.");
        }
        long clienteId = ((Number) vinculacion.get(0).get("cliente_id")).longValue();
        List<Map<String, Object>> clientes = db.queryForList(
                "SELECT cliente_id AS id, tipo_documento AS tipoDocumento, numero_documento AS numeroDocumento, " +
                        "nombres, apellidos, telefono, direccion FROM cliente WHERE cliente_id=? AND activo=1", clienteId);
        if (clientes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La ficha de cliente está inactiva.");
        }
        List<Map<String, Object>> mascotas = db.queryForList(
                "SELECT m.mascota_id AS id, m.nombre_mascota AS nombre, m.edad, r.nombre AS raza, r.especie " +
                        "FROM mascota m LEFT JOIN raza r ON r.raza_id=m.raza_id " +
                        "WHERE m.cliente_id=? ORDER BY m.nombre_mascota", clienteId);
        List<Map<String, Object>> citas = db.queryForList(
                "SELECT c.id, c.fecha, c.motivo, c.estado, m.nombre_mascota AS mascota, " +
                        "CONCAT_WS(' ', md.nombres, md.apellidos) AS medico " +
                        "FROM citas c JOIN mascota m ON m.mascota_id=c.mascota_id " +
                        "LEFT JOIN medico md ON md.id=c.medico_id " +
                        "WHERE m.cliente_id=? AND c.fecha>=NOW() AND UPPER(c.estado) NOT IN ('CANCELADA','ANULADA') " +
                        "ORDER BY c.fecha LIMIT 20", clienteId);
        return Map.of("cliente", clientes.get(0), "mascotas", mascotas, "proximasCitas", citas);
    }
}
