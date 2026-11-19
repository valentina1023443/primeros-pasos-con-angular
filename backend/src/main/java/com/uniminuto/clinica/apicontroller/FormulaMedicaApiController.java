package com.uniminuto.clinica.apicontroller;

import com.uniminuto.clinica.api.FormulaMedicaApi;
import com.uniminuto.clinica.models.FormulaDetalleRs;
import com.uniminuto.clinica.models.FormulaMedicaRq;
import com.uniminuto.clinica.models.FormulaResumenRs;
import com.uniminuto.clinica.models.MiRespuestaRS;
import com.uniminuto.clinica.service.FormulaMedicaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
public class FormulaMedicaApiController implements FormulaMedicaApi {
    private final FormulaMedicaService formulaService;
    private final JdbcTemplate db;
    private final HttpServletRequest request;

    public FormulaMedicaApiController(FormulaMedicaService formulaService, JdbcTemplate db, HttpServletRequest request) {
        this.formulaService = formulaService;
        this.db = db;
        this.request = request;
    }

    @Override
    public ResponseEntity<List<FormulaResumenRs>> listar() {
        Long medico = medicoActual();
        List<FormulaResumenRs> formulas = formulaService.listar();
        return ResponseEntity.ok(medico == null ? formulas : formulas.stream().filter(f -> {
            try { return formulaService.consultar(f.getId()).getMedicoId().equals(medico); }
            catch (RuntimeException e) { return false; }
        }).toList());
    }

    @Override
    public ResponseEntity<FormulaDetalleRs> consultar(Long id) {
        FormulaDetalleRs formula = formulaService.consultar(id);
        Long medico = medicoActual();
        if (medico != null && !medico.equals(formula.getMedicoId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede consultar fórmulas propias");
        return ResponseEntity.ok(formula);
    }

    @Override
    @Transactional
    public ResponseEntity<MiRespuestaRS> crear(FormulaMedicaRq rq) {
        Long medico = medicoActual();
        bloquearCitaDeHistoria(rq == null ? null : rq.getHistoriaId());
        if (medico != null) rq.setMedicoId(medico);
        return ResponseEntity.status(HttpStatus.CREATED).body(formulaService.crear(rq));
    }

    @Override
    @Transactional
    public ResponseEntity<MiRespuestaRS> editar(Long id, FormulaMedicaRq rq) {
        Long medico = medicoActual();
        if (medico != null) {
            exigirPropiedad(id, medico);
            rq.setMedicoId(medico);
        }
        bloquearCitaDeFormula(id);
        validarSinDespachoIniciado(id);
        return ResponseEntity.ok(formulaService.editar(id, rq));
    }

    @Override
    @Transactional
    public ResponseEntity<MiRespuestaRS> inactivar(Long id) {
        Long medico = medicoActual();
        if (medico != null) exigirPropiedad(id, medico);
        bloquearCitaDeFormula(id);
        validarSinDespachoIniciado(id);
        return ResponseEntity.ok(formulaService.inactivar(id));
    }

    private Long medicoActual() {
        Object role = request.getAttribute(BearerSessionFilter.USER_ROLE);
        if (!"MEDICO".equals(role)) return null;
        Object user = request.getAttribute(BearerSessionFilter.USER_ID);
        if (!(user instanceof Number id)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión médica inválida");
        Long medico = db.query("SELECT medico_id FROM usuario WHERE id=?", rs -> rs.next() ? rs.getObject(1, Long.class) : null, id.longValue());
        if (medico == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta no está vinculada a un veterinario");
        return medico;
    }

    private void exigirPropiedad(Long formulaId, Long medicoId) {
        if (db.queryForList("SELECT formula_id FROM formula_medica WHERE formula_id=? AND medico_id=?", formulaId, medicoId).isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede modificar fórmulas propias");
    }

    private long bloquearCitaDeHistoria(Long historiaId) {
        if (historiaId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La historia clínica es obligatoria");
        List<Map<String, Object>> rows = db.queryForList("SELECT cita_id FROM historias_medicas WHERE id=?", historiaId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Historia clínica no encontrada");
        Object rawCita = rows.get(0).get("cita_id");
        if (!(rawCita instanceof Number citaNumber))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La historia clínica no está asociada a una cita atendida");
        long citaId = citaNumber.longValue();
        Map<String, Object> cita = db.query("SELECT id,estado FROM citas WHERE id=? FOR UPDATE", rs -> {
            if (!rs.next()) return null;
            return Map.of("id", rs.getLong("id"), "estado", rs.getString("estado"));
        }, citaId);
        if (cita == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita de la historia no encontrada");
        if (!"ATENDIDA".equalsIgnoreCase(String.valueOf(cita.get("estado"))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se formulan citas atendidas");
        exigirCitaSinFactura(citaId);
        return citaId;
    }

    private void bloquearCitaDeFormula(Long formulaId) {
        List<Map<String, Object>> rows = db.queryForList("SELECT COALESCE(f.cita_id,h.cita_id) cita_id FROM formula_medica f LEFT JOIN historias_medicas h ON h.id=f.historia_medica_id WHERE f.formula_id=?", formulaId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fórmula no encontrada");
        Object rawCita = rows.get(0).get("cita_id");
        if (!(rawCita instanceof Number citaNumber)) return;
        long citaId = citaNumber.longValue();
        db.queryForList("SELECT id FROM citas WHERE id=? FOR UPDATE", citaId);
        db.queryForList("SELECT formula_id FROM formula_medica WHERE formula_id=? FOR UPDATE", formulaId);
        exigirCitaSinFactura(citaId);
    }

    private void exigirCitaSinFactura(long citaId) {
        if (!db.queryForList("SELECT id FROM factura WHERE cita_id=?", citaId).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede cambiar la prescripción después de facturar la atención");
    }

    private void validarSinDespachoIniciado(long formulaId) {
        List<Map<String, Object>> rows = db.queryForList("SELECT d.estado,COALESCE(SUM(dd.cantidad_entregada),0) entregada FROM despacho_medicamento d LEFT JOIN despacho_detalle dd ON dd.despacho_id=d.id WHERE d.formula_id=? GROUP BY d.id,d.estado", formulaId);
        for (Map<String, Object> row : rows) {
            int entregada = ((Number) row.get("entregada")).intValue();
            if (entregada > 0 || !"CANCELADO".equals(String.valueOf(row.get("estado"))))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede modificar o inactivar una fórmula con despacho iniciado; conserve la trazabilidad de inventario");
        }
    }
}
