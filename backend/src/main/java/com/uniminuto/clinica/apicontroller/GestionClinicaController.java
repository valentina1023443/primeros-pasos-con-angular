package com.uniminuto.clinica.apicontroller;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/gestion")
@CrossOrigin(origins = "http://localhost:4200")
public class GestionClinicaController {
  private final JdbcTemplate db;
  public GestionClinicaController(JdbcTemplate db) { this.db = db; }
  private void role(String got, String... allowed) { if (got == null || Arrays.stream(allowed).noneMatch(x -> x.equalsIgnoreCase(got))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Perfil no autorizado"); }

  @GetMapping("/citas/calendario")
  public List<Map<String,Object>> calendario(@RequestParam(required=false) Long medicoId, @RequestParam String desde, @RequestParam String hasta, @RequestHeader(value="X-Role", required=false) String perfil, @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long actorId) {
    role(perfil, "ADMINISTRADOR", "SECRETARIA", "MEDICO");
    if ("MEDICO".equalsIgnoreCase(perfil)) {
      Number propio = db.queryForObject("SELECT medico_id FROM usuario WHERE id=?", Number.class, actorId);
      if (propio == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"La cuenta médica no está vinculada a un veterinario");
      medicoId = propio.longValue();
    }
    String sql="SELECT c.id,c.fecha,c.fecha_fin,c.estado,c.motivo,c.observaciones,c.mascota_id,m.nombre_mascota mascota,cl.cliente_id,CONCAT(cl.nombres,' ',cl.apellidos) propietario,c.medico_id,CONCAT(md.nombres,' ',md.apellidos) veterinario FROM citas c JOIN mascota m ON m.mascota_id=c.mascota_id JOIN cliente cl ON cl.cliente_id=m.cliente_id JOIN medico md ON md.id=c.medico_id WHERE c.fecha < ? AND COALESCE(c.fecha_fin,c.fecha) >= ?";
    List<Object> args=new ArrayList<>(List.of(hasta+" 23:59:59", desde+" 00:00:00")); if(medicoId!=null){sql+=" AND c.medico_id=?";args.add(medicoId);} sql+=" ORDER BY c.fecha"; return db.queryForList(sql,args.toArray());
  }

  @PostMapping("/citas") @Transactional
  public Map<String,Object> crearCita(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long actorId,@RequestBody Map<String,Object> b) { role(perfil,"ADMINISTRADOR","SECRETARIA","MEDICO"); return guardarCita(null,b,perfil,actorId); }
  @PutMapping("/citas/{id}") @Transactional
  public Map<String,Object> editarCita(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long actorId,@PathVariable long id,@RequestBody Map<String,Object> b) { role(perfil,"ADMINISTRADOR","SECRETARIA","MEDICO"); return guardarCita(id,b,perfil,actorId); }
  private Map<String,Object> guardarCita(Long id, Map<String,Object> b, String perfil, long actorId) {
    if (b.get("mascotaId")==null || b.get("medicoId")==null || b.get("inicio")==null || b.get("fin")==null || String.valueOf(b.getOrDefault("motivo","")).isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mascota, veterinario, inicio, fin y motivo son obligatorios");
    long mascota=((Number)b.get("mascotaId")).longValue(), medico=((Number)b.get("medicoId")).longValue();
    if ("MEDICO".equalsIgnoreCase(perfil)) { Number propio=db.queryForObject("SELECT medico_id FROM usuario WHERE id=?",Number.class,actorId); if(propio==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"La cuenta médica no está vinculada a un veterinario"); medico=propio.longValue(); }
    java.time.LocalDateTime start=parseDateTime(b.get("inicio")), end=parseDateTime(b.get("fin"));
    if (!end.isAfter(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La hora final debe ser posterior a la hora de inicio");
    String inicio=start.toString().replace('T',' '), fin=end.toString().replace('T',' ');
    if(db.queryForList("SELECT id FROM medico WHERE id=? AND COALESCE(activo,1)=1",medico).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El veterinario está inactivo o no existe");
    if(db.queryForList("SELECT mascota_id FROM mascota WHERE mascota_id=? AND cliente_id=?",mascota,b.get("clienteId")).isEmpty() && b.get("clienteId")!=null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La mascota no pertenece al propietario indicado");
    List<Map<String,Object>> available=db.queryForList("SELECT id FROM horario_disponibilidad WHERE medico_id=? AND dia_semana=? AND activo=1 AND hora_inicio<=? AND hora_fin>=?",medico,start.getDayOfWeek().getValue(),start.toLocalTime(),end.toLocalTime());
    if(available.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"El veterinario no tiene disponibilidad para ese día y horario");
    String overlap="SELECT id FROM citas WHERE medico_id=? AND estado<>'CANCELADA' AND fecha < ? AND COALESCE(fecha_fin,DATE_ADD(fecha,INTERVAL 30 MINUTE)) > ?"+(id==null?"":" AND id<>?"); List<Object> args=new ArrayList<>(List.of(medico,fin,inicio)); if(id!=null)args.add(id); if(!db.queryForList(overlap,args.toArray()).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"El veterinario ya tiene una cita que se superpone");
    String petOverlap=overlap.replace("medico_id=?","mascota_id=?"); args=new ArrayList<>(List.of(mascota,fin,inicio)); if(id!=null)args.add(id); if(!db.queryForList(petOverlap,args.toArray()).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"La mascota ya tiene una cita que se superpone");
    String estado=String.valueOf(b.getOrDefault("estado","PENDIENTE")).toUpperCase(Locale.ROOT);
    if(!List.of("PENDIENTE","CONFIRMADA","PROGRAMADA").contains(estado)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Estado inicial de cita no permitido");
    if(id==null){db.update("INSERT INTO citas(mascota_id,medico_id,fecha,fecha_fin,motivo,observaciones,estado) VALUES(?,?,?,?,?,?,?)",mascota,medico,inicio,fin,b.get("motivo"),b.get("observaciones"),estado); id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
    else { Map<String,Object> old=db.queryForMap("SELECT estado FROM citas WHERE id=? FOR UPDATE",id); db.update("UPDATE citas SET mascota_id=?,medico_id=?,fecha=?,fecha_fin=?,motivo=?,observaciones=?,estado=? WHERE id=?",mascota,medico,inicio,fin,b.get("motivo"),b.get("observaciones"),estado,id); if(!estado.equalsIgnoreCase(String.valueOf(old.get("estado")))) db.update("INSERT INTO cita_estado_log(cita_id,estado_anterior,estado_nuevo,usuario_id,motivo) VALUES(?,?,?,?,?)",id,old.get("estado"),estado,actorId,"Cambio de estado"); }
    return Map.of("id",id,"message","Cita guardada y disponibilidad validada","inicio",inicio,"fin",fin,"estado",estado);
  }

  private java.time.LocalDateTime parseDateTime(Object value) { try { return java.time.LocalDateTime.parse(String.valueOf(value).replace(' ','T')); } catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Fecha/hora inválida; use formato ISO local"); } }

  @PostMapping("/citas/{id}/cancelar") @Transactional
  public Map<String,Object> cancelarCita(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long actorId,@PathVariable long id,@RequestBody Map<String,Object> b) {
    role(perfil,"ADMINISTRADOR","SECRETARIA","MEDICO");
    String motivo=String.valueOf(b.getOrDefault("motivo","" )).trim();
    if(motivo.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Indique el motivo de cancelación");
    Map<String,Object> current=db.queryForMap("SELECT estado,medico_id FROM citas WHERE id=? FOR UPDATE",id);
    if("MEDICO".equalsIgnoreCase(perfil)) { Number propio=db.queryForObject("SELECT medico_id FROM usuario WHERE id=?",Number.class,actorId); if(propio==null || ((Number)current.get("medico_id")).longValue()!=propio.longValue()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo puede cancelar sus propias citas"); }
    String old=String.valueOf(current.get("estado"));
    if("ATENDIDA".equals(old)) throw new ResponseStatusException(HttpStatus.CONFLICT,"No se puede cancelar una cita ya atendida");
    if("CANCELADA".equals(old)) return Map.of("id",id,"estado",old,"message","La cita ya estaba cancelada");
    db.update("UPDATE citas SET estado='CANCELADA',cancelada_en=NOW(),cancelada_por=?,motivo_cancelacion=? WHERE id=?",actorId,motivo,id);
    db.update("INSERT INTO cita_estado_log(cita_id,estado_anterior,estado_nuevo,usuario_id,motivo) VALUES(?,?,?,?,?)",id,old,"CANCELADA",actorId,motivo);
    return Map.of("id",id,"estado","CANCELADA","message","Cancelación registrada en el historial");
  }

  @PostMapping("/citas/{id}/reactivar") @Transactional
  public Map<String,Object> reactivarCita(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long actorId,@PathVariable long id) {
    role(perfil,"ADMINISTRADOR","SECRETARIA","MEDICO");
    Map<String,Object> c=db.queryForMap("SELECT c.mascota_id,c.medico_id,c.fecha,COALESCE(c.fecha_fin,DATE_ADD(c.fecha,INTERVAL 30 MINUTE)) fecha_fin,c.motivo,c.observaciones,ma.cliente_id FROM citas c JOIN mascota ma ON ma.mascota_id=c.mascota_id WHERE c.id=? AND c.estado='CANCELADA' FOR UPDATE",id);
    if("MEDICO".equalsIgnoreCase(perfil)) { Number propio=db.queryForObject("SELECT medico_id FROM usuario WHERE id=?",Number.class,actorId); if(propio==null || ((Number)c.get("medico_id")).longValue()!=propio.longValue()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo puede reactivar sus propias citas"); }
    Map<String,Object> body=new HashMap<>(); body.put("mascotaId",c.get("mascota_id")); body.put("medicoId",c.get("medico_id")); body.put("clienteId",c.get("cliente_id")); body.put("inicio",String.valueOf(c.get("fecha")).replace(' ','T')); body.put("fin",String.valueOf(c.get("fecha_fin")).replace(' ','T')); body.put("motivo",c.get("motivo")); body.put("observaciones",c.get("observaciones")); body.put("estado","PENDIENTE");
    Map<String,Object> result=guardarCita(id,body,perfil,actorId);
    db.update("UPDATE citas SET cancelada_en=NULL,cancelada_por=NULL,motivo_cancelacion=NULL WHERE id=?",id);
    return Map.of("id",id,"estado","PENDIENTE","message","Cita reactivada; se conservó el evento de cancelación","validacion",result.get("message"));
  }

  @GetMapping("/citas/{id}/historial")
  public List<Map<String,Object>> historialCita(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@PathVariable long id) {
    role(perfil,"ADMINISTRADOR","SECRETARIA","MEDICO");
    return db.queryForList("SELECT l.estado_anterior,l.estado_nuevo,l.fecha,l.motivo,u.username usuario FROM cita_estado_log l JOIN usuario u ON u.id=l.usuario_id WHERE l.cita_id=? ORDER BY l.fecha,l.id",id);
  }

  @GetMapping("/horarios/{medicoId}") public List<Map<String,Object>> horarios(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long actorId,@PathVariable long medicoId){role(perfil,"ADMINISTRADOR","SECRETARIA","MEDICO");if("MEDICO".equalsIgnoreCase(perfil)){Number propio=db.queryForObject("SELECT medico_id FROM usuario WHERE id=?",Number.class,actorId);if(propio==null||propio.longValue()!=medicoId)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo puede consultar su propio horario");}return db.queryForList("SELECT * FROM horario_disponibilidad WHERE medico_id=? AND activo=1 ORDER BY dia_semana,hora_inicio",medicoId);}
  @PostMapping("/horarios/{medicoId}") public Map<String,Object> horario(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@PathVariable long medicoId,@RequestBody Map<String,Object>b){role(perfil,"ADMINISTRADOR");db.update("INSERT INTO horario_disponibilidad(medico_id,dia_semana,hora_inicio,hora_fin) VALUES(?,?,?,?)",medicoId,b.get("diaSemana"),b.get("horaInicio"),b.get("horaFin"));return Map.of("id",db.queryForObject("SELECT LAST_INSERT_ID()",Long.class));}

  @GetMapping("/tarifas") public List<Map<String,Object>> tarifas(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil){role(perfil,"ADMINISTRADOR","SECRETARIA");return db.queryForList("SELECT t.*,e.nombre especialidad FROM tarifa_servicio t LEFT JOIN especializacion e ON e.id=t.especializacion_id ORDER BY t.activa DESC,t.servicio");}
  @PostMapping("/tarifas") @Transactional public Map<String,Object> tarifa(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long user,@RequestBody Map<String,Object>b){role(perfil,"ADMINISTRADOR");validarIva(b.getOrDefault("ivaPorcentaje",19));if(String.valueOf(b.getOrDefault("servicio","")).isBlank()||b.get("valor")==null||new java.math.BigDecimal(String.valueOf(b.get("valor"))).signum()<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Servicio y tarifa no negativa son obligatorios");db.update("INSERT INTO tarifa_servicio(especializacion_id,servicio,valor,iva_porcentaje,vigente_desde) VALUES(?,?,?,?,?)",b.get("especializacionId"),b.get("servicio"),b.get("valor"),b.getOrDefault("ivaPorcentaje",19),b.getOrDefault("vigenteDesde",LocalDate.now()));Long tariff=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);db.update("INSERT INTO tarifa_log(tarifa_id,valor_anterior,valor_nuevo,usuario_id) VALUES(?,NULL,?,?)",tariff,b.get("valor"),user);return Map.of("id",tariff,"message","Tarifa creada y auditada");}
  @PutMapping("/tarifas/{id}") @Transactional public Map<String,Object> editarTarifa(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long user,@PathVariable long id,@RequestBody Map<String,Object>b){role(perfil,"ADMINISTRADOR");validarIva(b.getOrDefault("ivaPorcentaje",19));if(b.get("valor")==null||new java.math.BigDecimal(String.valueOf(b.get("valor"))).signum()<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La tarifa debe ser un número no negativo");Number old=db.queryForObject("SELECT valor FROM tarifa_servicio WHERE id=?",Number.class,id);db.update("UPDATE tarifa_servicio SET valor=?,iva_porcentaje=?,activa=? WHERE id=?",b.get("valor"),b.getOrDefault("ivaPorcentaje",19),b.getOrDefault("activa",true),id);db.update("INSERT INTO tarifa_log(tarifa_id,valor_anterior,valor_nuevo,usuario_id) VALUES(?,?,?,?)",id,old,b.get("valor"),user);return Map.of("message","Tarifa actualizada");}

  @PostMapping("/prefacturas/{citaId}") @Transactional
  public Map<String,Object> prefactura(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@PathVariable long citaId,@RequestParam(defaultValue="Consulta veterinaria") String servicio){
    role(perfil,"ADMINISTRADOR","SECRETARIA");
    if(!db.queryForList("SELECT id FROM prefactura WHERE cita_id=?",citaId).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"La cita ya tiene prefactura");
    Map<String,Object> c=db.queryForMap("SELECT c.mascota_id,md.especializacion_id FROM citas c JOIN medico md ON md.id=c.medico_id WHERE c.id=? AND c.estado='ATENDIDA'",citaId);
    if(servicio==null||servicio.isBlank()||servicio.length()>120)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Servicio de prefactura obligatorio (máximo 120 caracteres)");
    List<Map<String,Object>> rates=db.queryForList("SELECT id,servicio,valor,iva_porcentaje FROM tarifa_servicio WHERE activa=1 AND servicio=? AND vigente_desde<=CURDATE() AND (vigente_hasta IS NULL OR vigente_hasta>=CURDATE()) AND (especializacion_id=? OR especializacion_id IS NULL) ORDER BY (especializacion_id IS NULL),vigente_desde DESC,id DESC LIMIT 1",servicio.trim(),c.get("especializacion_id"));
    if(rates.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"No hay tarifa vigente para la especialidad");
    Map<String,Object> tariff=rates.get(0);java.math.BigDecimal subtotal=new java.math.BigDecimal(String.valueOf(tariff.get("valor"))).setScale(2,java.math.RoundingMode.HALF_UP);java.math.BigDecimal rate=new java.math.BigDecimal(String.valueOf(tariff.get("iva_porcentaje"))).setScale(2,java.math.RoundingMode.HALF_UP);java.math.BigDecimal tax=subtotal.multiply(rate).divide(new java.math.BigDecimal("100"),2,java.math.RoundingMode.HALF_UP);
    List<Map<String,Object>> prescribed=db.queryForList("SELECT f.formula_id,f.cantidad_prescrita,m.nombre medicamento,COALESCE((SELECT i.precio_venta FROM medicamento_inventario i WHERE i.medicamento_id=f.medicamento_id AND i.activo=1 ORDER BY i.id LIMIT 1),0) precio,COALESCE((SELECT i.iva_porcentaje FROM medicamento_inventario i WHERE i.medicamento_id=f.medicamento_id AND i.activo=1 ORDER BY i.id LIMIT 1),19) iva FROM formula_medica f JOIN medicamento m ON m.medicamento_id=f.medicamento_id WHERE f.cita_id=? AND f.estado='ACTIVA' ORDER BY f.formula_id",citaId);
    for(Map<String,Object> product:prescribed){java.math.BigDecimal unit=new java.math.BigDecimal(String.valueOf(product.get("precio"))).setScale(2,java.math.RoundingMode.HALF_UP);java.math.BigDecimal productRate=new java.math.BigDecimal(String.valueOf(product.get("iva"))).setScale(2,java.math.RoundingMode.HALF_UP);int quantity=((Number)product.get("cantidad_prescrita")).intValue();java.math.BigDecimal line=unit.multiply(java.math.BigDecimal.valueOf(quantity));subtotal=subtotal.add(line);tax=tax.add(line.multiply(productRate).divide(new java.math.BigDecimal("100"),2,java.math.RoundingMode.HALF_UP));}
    java.math.BigDecimal total=subtotal.add(tax);
    db.update("INSERT INTO prefactura(cita_id,subtotal,iva,descuento,total) VALUES(?,?,?,0,?)",citaId,subtotal,tax,total);
    Long prefacturaId=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    db.update("INSERT INTO prefactura_detalle(prefactura_id,concepto,cantidad,precio_unitario,iva_porcentaje) VALUES(?,?,1,?,?)",prefacturaId,tariff.get("servicio"),tariff.get("valor"),rate);
    for(Map<String,Object> product:prescribed)db.update("INSERT INTO prefactura_detalle(prefactura_id,concepto,cantidad,precio_unitario,iva_porcentaje) VALUES(?,?,?,?,?)",prefacturaId,"Medicamento formulado: "+product.get("medicamento"),product.get("cantidad_prescrita"),product.get("precio"),product.get("iva"));
    return Map.of("prefacturaId",prefacturaId,"citaId",citaId,"subtotal",subtotal,"iva",tax,"total",total,"tarifaId",tariff.get("id"),"message","Prefactura calculada con tarifa vigente");
  }

  @PostMapping("/atenciones/{citaId}") @Transactional
  public Map<String,Object> atencion(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long actorId,@PathVariable long citaId,@RequestBody Map<String,Object>b){
    role(perfil,"MEDICO","ADMINISTRADOR");
    Map<String,Object> c=db.queryForMap("SELECT mascota_id,medico_id,estado FROM citas WHERE id=? FOR UPDATE",citaId);
    if("MEDICO".equalsIgnoreCase(perfil)){Number propio=db.queryForObject("SELECT medico_id FROM usuario WHERE id=?",Number.class,actorId);if(propio==null||propio.longValue()!=((Number)c.get("medico_id")).longValue())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo puede registrar atenciones de sus propias citas");}
    if("CANCELADA".equals(c.get("estado"))||"ATENDIDA".equals(c.get("estado")))throw new ResponseStatusException(HttpStatus.CONFLICT,"La cita está cancelada o ya tiene atención registrada");
    if(String.valueOf(b.getOrDefault("diagnostico","")).isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El diagnóstico es obligatorio");
    if(!db.queryForList("SELECT id FROM historias_medicas WHERE cita_id=?",citaId).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"La cita ya tiene una historia clínica asociada");
    String notes=String.join(" | ",String.valueOf(b.getOrDefault("sintomas","")),String.valueOf(b.getOrDefault("observaciones","")),String.valueOf(b.getOrDefault("recomendaciones","")));
    db.update("INSERT INTO historias_medicas(mascota_id,cita_id,medico_id,fecha_consulta,fecha_creacion,motivo_consulta,diagnostico,observaciones_generales,estado) VALUES(?,?,?,?,?,?,?,?,?)",c.get("mascota_id"),citaId,c.get("medico_id"),LocalDateTime.now(),LocalDateTime.now(),b.getOrDefault("motivo","Atención médica"),b.get("diagnostico"),notes,"ABIERTA");
    db.update("UPDATE citas SET estado='ATENDIDA' WHERE id=?",citaId);
    db.update("INSERT INTO cita_estado_log(cita_id,estado_anterior,estado_nuevo,usuario_id,motivo) VALUES(?,?,?,?,?)",citaId,c.get("estado"),"ATENDIDA",actorId,"Atención clínica registrada");
    return Map.of("historiaId",db.queryForObject("SELECT LAST_INSERT_ID()",Long.class),"citaId",citaId,"message","Atención finalizada y auditada");
  }

  @PostMapping("/formulas") @Transactional
  public Map<String,Object> formula(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long actorId,@RequestBody Map<String,Object>b){
    role(perfil,"MEDICO","ADMINISTRADOR");
    for(String key:List.of("mascotaId","citaId","historiaId","medicoId","medicamentoId","dosis","frecuenciaHoras","duracionDias","cantidadPrescrita"))if(b.get(key)==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Falta el campo obligatorio: "+key);
    long citaId=((Number)b.get("citaId")).longValue();
    Map<String,Object> cita=db.queryForMap("SELECT mascota_id,medico_id,estado FROM citas WHERE id=? FOR UPDATE",citaId);
    if(!db.queryForList("SELECT id FROM factura WHERE cita_id=?",citaId).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"No se puede agregar una fórmula después de facturar la atención");
    if(!List.of("ATENDIDA").contains(String.valueOf(cita.get("estado"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"La fórmula debe asociarse a una cita atendida");
    if(((Number)cita.get("mascota_id")).longValue()!=((Number)b.get("mascotaId")).longValue())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La mascota no coincide con la cita");
    long medico=((Number)cita.get("medico_id")).longValue();
    if("MEDICO".equalsIgnoreCase(perfil)){Number propio=db.queryForObject("SELECT medico_id FROM usuario WHERE id=?",Number.class,actorId);if(propio==null||propio.longValue()!=medico)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo puede formular para sus propias atenciones");}
    if(medico!=((Number)b.get("medicoId")).longValue())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El veterinario de la fórmula no coincide con la atención");
    if(db.queryForList("SELECT id FROM historias_medicas WHERE id=? AND cita_id=? AND mascota_id=?",b.get("historiaId"),citaId,b.get("mascotaId")).isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La historia clínica no corresponde a la cita y mascota indicadas");
    int quantity=((Number)b.get("cantidadPrescrita")).intValue(), frequency=((Number)b.get("frecuenciaHoras")).intValue(), days=((Number)b.get("duracionDias")).intValue();
    if(quantity<1||quantity>10000||frequency<1||frequency>72||days<1||days>365)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cantidad, frecuencia o duración fuera de rango");
    if(new java.math.BigDecimal(String.valueOf(b.get("dosis"))).signum()<=0||String.valueOf(b.getOrDefault("indicaciones","")).isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La dosis debe ser positiva y las indicaciones son obligatorias");
    if(db.queryForList("SELECT medicamento_id FROM medicamento WHERE medicamento_id=?",b.get("medicamentoId")).isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El medicamento no existe");
    LocalDateTime issued=b.get("fechaEmision")==null?LocalDateTime.now():parseDateTime(b.get("fechaEmision"));
    db.update("INSERT INTO formula_medica(mascota_id,cita_id,historia_medica_id,medico_id,medicamento_id,fecha_creacion,fecha_emision,dosis,unidad_dosis,frecuencia_horas,duracion_dias,cantidad_prescrita,via_administracion,indicaciones,estado) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",b.get("mascotaId"),citaId,b.get("historiaId"),medico,b.get("medicamentoId"),LocalDateTime.now(),issued,b.get("dosis"),b.getOrDefault("unidadDosis","mg"),frequency,days,quantity,b.get("viaAdministracion"),b.get("indicaciones"),"ACTIVA");
    return Map.of("formulaId",db.queryForObject("SELECT LAST_INSERT_ID()",Long.class),"message","Fórmula creada y disponibilidad visible en el inventario");
  }

  @GetMapping("/reportes/{tipo}") public ResponseEntity<byte[]> reporte(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@PathVariable String tipo,@RequestParam String desde,@RequestParam String hasta,@RequestParam(defaultValue="xlsx") String formato) throws Exception {role(perfil,"ADMINISTRADOR","SECRETARIA");List<Map<String,Object>> rows=reporteRows(tipo,desde,hasta);String title="Reporte "+tipo+" | "+desde+" a "+hasta; if(formato.equalsIgnoreCase("pdf")){ByteArrayOutputStream out=new ByteArrayOutputStream();Document d=new Document();PdfWriter.getInstance(d,out);d.open();d.add(new Paragraph(title));for(Map<String,Object>r:rows)d.add(new Paragraph(r.toString()));d.close();return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+tipo+".pdf").body(out.toByteArray());}try(XSSFWorkbook wb=new XSSFWorkbook()){var sh=wb.createSheet("Reporte");int n=0;sh.createRow(n++).createCell(0).setCellValue(title);for(Map<String,Object>r:rows){var row=sh.createRow(n++);int c=0;for(Object v:r.values())row.createCell(c++).setCellValue(String.valueOf(v));}ByteArrayOutputStream out=new ByteArrayOutputStream();wb.write(out);return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+tipo+".xlsx").body(out.toByteArray());}}
  private List<Map<String,Object>> reporteRows(String tipo,String desde,String hasta){return switch(tipo.toLowerCase()){case "financiero","facturas"->db.queryForList("SELECT estado,COUNT(*) facturas,SUM(subtotal) subtotal,SUM(iva) iva,SUM(total) total,SUM(descuento) descuentos FROM factura WHERE fecha BETWEEN ? AND ? GROUP BY estado",desde+" 00:00:00",hasta+" 23:59:59");case "inventario"->db.queryForList("SELECT m.nombre,i.codigo,COALESCE(SUM(l.cantidad),0) existencias,SUM(l.cantidad*i.precio_compra) costo,SUM(l.cantidad*i.precio_venta) venta FROM medicamento_inventario i JOIN medicamento m ON m.medicamento_id=i.medicamento_id LEFT JOIN lote_medicamento l ON l.inventario_id=i.id GROUP BY i.id,m.nombre,i.codigo");default->db.queryForList("SELECT estado,COUNT(*) total FROM citas WHERE fecha BETWEEN ? AND ? GROUP BY estado",desde+" 00:00:00",hasta+" 23:59:59");};}

  @GetMapping("/documentos/{tipo}/{id}")
  public ResponseEntity<byte[]> documento(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String perfil,@PathVariable String tipo,@PathVariable long id)throws Exception{
    String t=tipo.toLowerCase(Locale.ROOT);
    switch(t){case "factura","prefactura"->role(perfil,"ADMINISTRADOR","SECRETARIA");case "formula"->role(perfil,"ADMINISTRADOR","MEDICO");case "despacho"->role(perfil,"ADMINISTRADOR","DESPACHO_MEDICO");default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tipo de documento no soportado");}
    ByteArrayOutputStream out=new ByteArrayOutputStream();Document d=new Document();PdfWriter.getInstance(d,out);d.open();
    d.add(new Paragraph("CLÍNICA VETERINARIA UNIMINUTO"));d.add(new Paragraph("Documento: "+t.toUpperCase(Locale.ROOT)+" #"+id));d.add(new Paragraph("Generado: "+LocalDateTime.now()));d.add(new Paragraph(" "));
    if(t.equals("factura")){
      Map<String,Object> f=db.queryForMap("SELECT f.*,cl.nombres,cl.apellidos,ma.nombre_mascota,c.fecha cita_fecha,CONCAT(md.nombres,' ',md.apellidos) veterinario,e.nombre especialidad FROM factura f JOIN cliente cl ON cl.cliente_id=f.cliente_id JOIN mascota ma ON ma.mascota_id=f.mascota_id JOIN citas c ON c.id=f.cita_id JOIN medico md ON md.id=c.medico_id LEFT JOIN especializacion e ON e.id=md.especializacion_id WHERE f.id=?",id);
      d.add(new Paragraph("Propietario: "+f.get("nombres")+" "+f.get("apellidos")));d.add(new Paragraph("Paciente: "+f.get("nombre_mascota")+" | Veterinario: "+f.get("veterinario")+" | Especialidad: "+f.get("especialidad")));d.add(new Paragraph("Fecha de atención: "+f.get("cita_fecha")+" | Fecha de factura: "+f.get("fecha")));d.add(new Paragraph("Estado: "+f.get("estado")));
      PdfPTable table=new PdfPTable(4);table.setWidthPercentage(100);for(String h:List.of("Concepto","Cantidad","Precio unitario","IVA %"))table.addCell(h);
      for(Map<String,Object> row:db.queryForList("SELECT concepto,cantidad,precio_unitario,iva_porcentaje FROM factura_detalle WHERE factura_id=? ORDER BY id",id)){table.addCell(String.valueOf(row.get("concepto")));table.addCell(String.valueOf(row.get("cantidad")));table.addCell(String.valueOf(row.get("precio_unitario")));table.addCell(String.valueOf(row.get("iva_porcentaje")));}d.add(table);
      d.add(new Paragraph("Subtotal: "+f.get("subtotal")+" | IVA: "+f.get("iva")+" | Descuento: "+f.get("descuento")+" | TOTAL: "+f.get("total")));
      d.add(new Paragraph("Pagos: "+db.queryForList("SELECT pagado_en,valor,medio FROM pago_factura WHERE factura_id=? ORDER BY pagado_en",id)));
    } else if(t.equals("formula")){
      Map<String,Object> f=db.queryForMap("SELECT f.formula_id,f.fecha_emision,f.dosis,f.unidad_dosis,f.frecuencia_horas,f.duracion_dias,f.cantidad_prescrita,f.via_administracion,f.indicaciones,f.estado,m.nombre medicamento,ma.nombre_mascota mascota,cl.nombres propietario,cl.apellidos apellido,md.nombres veterinario,md.apellidos veterinario_apellido FROM formula_medica f JOIN medicamento m ON m.medicamento_id=f.medicamento_id JOIN mascota ma ON ma.mascota_id=f.mascota_id JOIN cliente cl ON cl.cliente_id=ma.cliente_id JOIN medico md ON md.id=f.medico_id WHERE f.formula_id=?",id);
      d.add(new Paragraph("Paciente: "+f.get("mascota")+" | Propietario: "+f.get("propietario")+" "+f.get("apellido")));d.add(new Paragraph("Veterinario: "+f.get("veterinario")+" "+f.get("veterinario_apellido")+" | Fecha: "+f.get("fecha_emision")));d.add(new Paragraph("Medicamento: "+f.get("medicamento")));d.add(new Paragraph("Dosis: "+f.get("dosis")+" "+f.get("unidad_dosis")+" | Vía: "+f.get("via_administracion")));d.add(new Paragraph("Frecuencia: cada "+f.get("frecuencia_horas")+" horas | Duración: "+f.get("duracion_dias")+" días | Cantidad: "+f.get("cantidad_prescrita")));d.add(new Paragraph("Indicaciones: "+f.get("indicaciones")));d.add(new Paragraph("Estado: "+f.get("estado")));
    } else if(t.equals("despacho")){
      Map<String,Object> dispatch=db.queryForMap("SELECT d.id,d.formula_id,d.estado,d.entregado_en,u.username usuario FROM despacho_medicamento d JOIN usuario u ON u.id=d.usuario_id WHERE d.id=?",id);d.add(new Paragraph("Fórmula: "+dispatch.get("formula_id")+" | Estado: "+dispatch.get("estado")));d.add(new Paragraph("Responsable: "+dispatch.get("usuario")+" | Fecha de entrega: "+dispatch.get("entregado_en")));
      PdfPTable table=new PdfPTable(4);table.setWidthPercentage(100);for(String h:List.of("Medicamento","Lote","Vencimiento","Cantidad entregada"))table.addCell(h);
      for(Map<String,Object> row:db.queryForList("SELECT m.nombre medicamento,l.numero_lote,l.fecha_vencimiento,dd.cantidad_entregada FROM despacho_detalle dd JOIN medicamento_inventario i ON i.id=dd.medicamento_id JOIN medicamento m ON m.medicamento_id=i.medicamento_id JOIN lote_medicamento l ON l.id=dd.lote_id WHERE dd.despacho_id=? ORDER BY l.fecha_vencimiento",id)){table.addCell(String.valueOf(row.get("medicamento")));table.addCell(String.valueOf(row.get("numero_lote")));table.addCell(String.valueOf(row.get("fecha_vencimiento")));table.addCell(String.valueOf(row.get("cantidad_entregada")));}d.add(table);
    } else {
      Map<String,Object> p=db.queryForMap("SELECT p.*,c.fecha cita_fecha,ma.nombre_mascota mascota,cl.nombres propietario,cl.apellidos apellido,CONCAT(md.nombres,' ',md.apellidos) veterinario,e.nombre especialidad FROM prefactura p JOIN citas c ON c.id=p.cita_id JOIN mascota ma ON ma.mascota_id=c.mascota_id JOIN cliente cl ON cl.cliente_id=ma.cliente_id JOIN medico md ON md.id=c.medico_id LEFT JOIN especializacion e ON e.id=md.especializacion_id WHERE p.cita_id=?",id);d.add(new Paragraph("Paciente: "+p.get("mascota")+" | Propietario: "+p.get("propietario")+" "+p.get("apellido")));d.add(new Paragraph("Veterinario: "+p.get("veterinario")+" | Especialidad: "+p.get("especialidad")));d.add(new Paragraph("Cita: "+id+" | Fecha: "+p.get("cita_fecha")));PdfPTable table=new PdfPTable(4);table.setWidthPercentage(100);for(String h:List.of("Concepto","Cantidad","Precio unitario","IVA %"))table.addCell(h);for(Map<String,Object> row:db.queryForList("SELECT concepto,cantidad,precio_unitario,iva_porcentaje FROM prefactura_detalle WHERE prefactura_id=? ORDER BY id",p.get("id"))){table.addCell(String.valueOf(row.get("concepto")));table.addCell(String.valueOf(row.get("cantidad")));table.addCell(String.valueOf(row.get("precio_unitario")));table.addCell(String.valueOf(row.get("iva_porcentaje")));}d.add(table);d.add(new Paragraph("Subtotal: "+p.get("subtotal")+" | IVA: "+p.get("iva")+" | Descuento: "+p.get("descuento")+" | Total estimado: "+p.get("total")));
    }
    d.close();return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+t+"-"+id+".pdf").body(out.toByteArray());
  }
  private void validarIva(Object value){try{java.math.BigDecimal rate=new java.math.BigDecimal(String.valueOf(value));if(rate.signum()<0||rate.compareTo(new java.math.BigDecimal("100"))>0)throw new IllegalArgumentException();}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El IVA debe estar entre 0 y 100 por ciento");}}
}
