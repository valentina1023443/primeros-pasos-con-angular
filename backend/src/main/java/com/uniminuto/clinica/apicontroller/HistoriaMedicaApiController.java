package com.uniminuto.clinica.apicontroller;

import com.uniminuto.clinica.api.HistoriaMedicaApi;
import com.uniminuto.clinica.models.*;
import com.uniminuto.clinica.service.HistoriaMedicaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
public class HistoriaMedicaApiController implements HistoriaMedicaApi {
    private final HistoriaMedicaService service;
    private final JdbcTemplate db;
    private final HttpServletRequest request;
    public HistoriaMedicaApiController(HistoriaMedicaService service, JdbcTemplate db, HttpServletRequest request) {
        this.service=service; this.db=db; this.request=request;
    }

    @Override public ResponseEntity<List<HistoriaMedicaRs>> listar(){ Long medico=medicoActual(); List<HistoriaMedicaRs> rows=service.listar(); return ResponseEntity.ok(medico==null?rows:rows.stream().filter(h->medico.equals(h.getMedicoId())).toList()); }
    @Override public ResponseEntity<HistoriaMedicaRs> consultar(Long id){ exigirPropiedad(id); return ResponseEntity.ok(service.consultar(id)); }
    @Override public ResponseEntity<List<HistoriaMedicaRs>> listarPorMascota(Long mascotaId){ Long medico=medicoActual(); List<HistoriaMedicaRs> rows=service.listarPorMascota(mascotaId); return ResponseEntity.ok(medico==null?rows:rows.stream().filter(h->medico.equals(h.getMedicoId())).toList()); }
    @Override public ResponseEntity<MiRespuestaRS> crear(HistoriaMedicaRq rq){ Long medico=medicoActual(); if(medico!=null)rq.setMedicoId(medico); return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(rq)); }
    @Override public ResponseEntity<MiRespuestaRS> cerrar(Long id){ exigirPropiedad(id); return ResponseEntity.ok(service.cerrar(id)); }
    @Override public ResponseEntity<List<AnotacionRs>> listarAnotaciones(Long id){ exigirPropiedad(id); return ResponseEntity.ok(service.listarAnotaciones(id)); }
    @Override public ResponseEntity<AnotacionRs> consultarAnotacion(Long anotacionId){ exigirAnotacionPropia(anotacionId); return ResponseEntity.ok(service.consultarAnotacion(anotacionId)); }
    @Override public ResponseEntity<MiRespuestaRS> crearAnotacion(Long id, AnotacionRq rq){ exigirPropiedad(id); Long medico=medicoActual(); if(medico!=null)rq.setMedicoId(medico); return ResponseEntity.status(HttpStatus.CREATED).body(service.crearAnotacion(id,rq)); }

    private Long medicoActual(){
        if(!"MEDICO".equals(request.getAttribute(BearerSessionFilter.USER_ROLE)))return null;
        Object raw=request.getAttribute(BearerSessionFilter.USER_ID);
        if(!(raw instanceof Number user))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sesión médica inválida");
        Long medico=db.query("SELECT medico_id FROM usuario WHERE id=?",rs->rs.next()?rs.getObject(1,Long.class):null,user.longValue());
        if(medico==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"La cuenta no está vinculada a un veterinario");
        return medico;
    }
    private void exigirPropiedad(Long id){ Long medico=medicoActual(); if(medico!=null&&db.queryForList("SELECT id FROM historias_medicas WHERE id=? AND medico_id=?",id,medico).isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo puede acceder a historias propias"); }
    private void exigirAnotacionPropia(Long id){ Long medico=medicoActual(); if(medico!=null&&db.queryForList("SELECT a.id FROM anotaciones_historia a JOIN historias_medicas h ON h.id=a.historia_medica_id WHERE a.id=? AND (a.medico_id=? OR h.medico_id=?)",id,medico,medico).isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo puede acceder a anotaciones de sus historias"); }
}
