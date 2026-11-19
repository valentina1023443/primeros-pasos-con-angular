package com.uniminuto.clinica.apicontroller;

import com.uniminuto.clinica.api.CitaApi;
import com.uniminuto.clinica.models.CitaRq;
import com.uniminuto.clinica.models.CitaRs;
import com.uniminuto.clinica.models.MiRespuestaRS;
import com.uniminuto.clinica.service.CitaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
public class CitaApiController implements CitaApi {
    private final CitaService service;
    private final JdbcTemplate db;
    private final HttpServletRequest request;
    public CitaApiController(CitaService service,JdbcTemplate db,HttpServletRequest request){this.service=service;this.db=db;this.request=request;}

    @Override public ResponseEntity<List<CitaRs>> listar(){Long medico=medicoActual();List<CitaRs> rows=service.listar();return ResponseEntity.ok(medico==null?rows:rows.stream().filter(c->medico.equals(c.getMedicoId())).toList());}
    @Override public ResponseEntity<CitaRs> consultar(Long id){exigirPropiedad(id);return ResponseEntity.ok(service.consultar(id));}
    @Override public ResponseEntity<List<CitaRs>> listarPorMascota(Long mascotaId){Long medico=medicoActual();List<CitaRs> rows=service.listarPorMascota(mascotaId);return ResponseEntity.ok(medico==null?rows:rows.stream().filter(c->medico.equals(c.getMedicoId())).toList());}
    @Override public ResponseEntity<MiRespuestaRS> crear(CitaRq rq){Long medico=medicoActual();if(medico!=null)rq.setMedicoId(medico);return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(rq));}
    @Override public ResponseEntity<MiRespuestaRS> editar(Long id,CitaRq rq){Long medico=medicoActual();if(medico!=null){exigirPropiedad(id);rq.setMedicoId(medico);}return ResponseEntity.ok(service.editar(id,rq));}
    @Override public ResponseEntity<MiRespuestaRS> reactivar(Long id){exigirPropiedad(id);return ResponseEntity.ok(service.reactivar(id));}

    private Long medicoActual(){if(!"MEDICO".equals(request.getAttribute(BearerSessionFilter.USER_ROLE)))return null;Object raw=request.getAttribute(BearerSessionFilter.USER_ID);if(!(raw instanceof Number user))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sesión médica inválida");Long medico=db.query("SELECT medico_id FROM usuario WHERE id=?",rs->rs.next()?rs.getObject(1,Long.class):null,user.longValue());if(medico==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"La cuenta no está vinculada a un veterinario");return medico;}
    private void exigirPropiedad(Long id){Long medico=medicoActual();if(medico!=null&&db.queryForList("SELECT id FROM citas WHERE id=? AND medico_id=?",id,medico).isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo puede acceder a sus propias citas");}
}
