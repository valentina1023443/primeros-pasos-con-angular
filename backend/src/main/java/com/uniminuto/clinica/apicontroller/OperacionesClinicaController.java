package com.uniminuto.clinica.apicontroller;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/operaciones")
@CrossOrigin(origins = "http://localhost:4200")
public class OperacionesClinicaController {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public OperacionesClinicaController(JdbcTemplate db) { this.db = db; this.json=new ObjectMapper(); }

    private void requireRole(String role, String... allowed) {
        if (role == null || Arrays.stream(allowed).noneMatch(x -> x.equalsIgnoreCase(role)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El rol no tiene permiso para esta operación");
    }

    @GetMapping("/inventario")
    public List<Map<String,Object>> inventario(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@RequestParam(defaultValue = "") String texto) {
        requireRole(role,"ADMINISTRADOR","DESPACHO_MEDICO","MEDICO");
        return db.queryForList("SELECT i.id,i.medicamento_id,i.codigo,i.tipo,i.registro_invima,i.precio_compra,i.precio_venta,i.iva_porcentaje,i.fecha_ingreso,i.stock_minimo,m.nombre,m.presentacion,COALESCE(SUM(CASE WHEN l.fecha_vencimiento>=CURDATE() THEN l.cantidad ELSE 0 END),0) stock,COALESCE(SUM(CASE WHEN l.fecha_vencimiento<CURDATE() THEN l.cantidad ELSE 0 END),0) stock_vencido,MIN(CASE WHEN l.fecha_vencimiento>=CURDATE() AND l.cantidad>0 THEN l.fecha_vencimiento END) proximo_vencimiento FROM medicamento_inventario i JOIN medicamento m ON m.medicamento_id=i.medicamento_id LEFT JOIN lote_medicamento l ON l.inventario_id=i.id WHERE i.activo=1 AND (LOWER(m.nombre) LIKE LOWER(?) OR i.codigo LIKE ?) GROUP BY i.id,i.medicamento_id,m.nombre,m.presentacion", "%"+texto+"%", "%"+texto+"%");
    }

    @PostMapping("/inventario") @Transactional
    public Map<String,Object> crearInventario(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long usuario,@RequestBody Map<String,Object> body) {
        requireRole(role, "ADMINISTRADOR");
        validarProducto(body);
        if(!db.queryForList("SELECT id FROM medicamento_inventario WHERE codigo=?",body.get("codigo")).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"El código de inventario ya existe");
        Number medId = findNumber("SELECT medicamento_id FROM medicamento WHERE LOWER(nombre)=LOWER(?)", body.get("nombre"));
        if (medId == null) { db.update("INSERT INTO medicamento(nombre,presentacion,descripcion) VALUES(?,?,?)", body.get("nombre"), body.get("presentacion"), body.get("descripcion")); medId = db.queryForObject("SELECT LAST_INSERT_ID()", Number.class); }
        db.update("INSERT INTO medicamento_inventario(medicamento_id,codigo,tipo,registro_invima,precio_compra,precio_venta,stock_minimo,fecha_ingreso,iva_porcentaje) VALUES(?,?,?,?,?,?,?,?,?)", medId, body.get("codigo"), body.getOrDefault("tipo","MEDICAMENTO"), body.get("registroInvima"), body.get("precioCompra"), body.get("precioVenta"), body.getOrDefault("stockMinimo",0),body.getOrDefault("fechaIngreso",LocalDate.now()),body.getOrDefault("ivaPorcentaje",19));
        Number invId = db.queryForObject("SELECT LAST_INSERT_ID()", Number.class);
        if(body.get("fechaVencimiento")==null || LocalDate.parse(String.valueOf(body.get("fechaVencimiento"))).isBefore(LocalDate.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La fecha de vencimiento debe ser futura");
        int qty=((Number)body.getOrDefault("cantidad",0)).intValue();
        if(qty<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La cantidad no puede ser negativa");
        db.update("INSERT INTO lote_medicamento(inventario_id,numero_lote,fecha_ingreso,fecha_vencimiento,cantidad) VALUES(?,?,?,?,?)", invId, body.get("lote"), body.getOrDefault("fechaIngreso", LocalDate.now()), body.get("fechaVencimiento"), qty);
        Number lotId=db.queryForObject("SELECT LAST_INSERT_ID()",Number.class);
        if(qty>0) db.update("INSERT INTO movimiento_inventario(inventario_id,lote_id,tipo,cantidad,usuario_id,referencia) VALUES(?,?,'ENTRADA',?,?,?)",invId,lotId,qty,usuario,"CREACION_INVENTARIO");
        return Map.of("id", invId, "message", "Producto y lote registrados; entrada de inventario auditada");
    }

    @PostMapping(value = "/inventario/importar/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Map<String,Object> previewImport(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long usuario,@RequestPart MultipartFile file) throws IOException {
        requireRole(role, "ADMINISTRADOR");
        if(file.isEmpty()||file.getSize()>5*1024*1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El archivo debe tener contenido y no superar 5 MB");
        String filename=String.valueOf(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if(!filename.endsWith(".csv")&&!filename.endsWith(".xlsx")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use un archivo .csv o .xlsx");
        List<Map<String,Object>> validos = new ArrayList<>(); List<Map<String,Object>> errores = new ArrayList<>();
        List<String[]> rows = parseRows(file); if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El archivo no tiene registros");
        if(rows.size()>5001) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El archivo supera el máximo de 5000 filas de datos");
        String[] expected = {"codigo","nombre","tipo","lote","precioCompra","precioVenta","cantidad","stockMinimo","fechaIngreso","fechaVencimiento","registroInvima"};
        String[] headers = rows.get(0); if(headers.length!=expected.length)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El archivo debe tener exactamente 11 columnas"); for (int i=0;i<expected.length;i++) if (!expected[i].equalsIgnoreCase(headers[i].trim())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Encabezados inválidos. Se esperaba: "+String.join(",",expected));
        Set<String> codes = new HashSet<>();
        for (int r=1;r<rows.size();r++) {
            String[] v=rows.get(r);
            if(Arrays.stream(v).allMatch(String::isBlank)) continue;
            Map<String,Object> error=new LinkedHashMap<>(); error.put("fila",r+1); List<String> reasons=new ArrayList<>();
            if(v.length!=11) reasons.add("cada fila debe contener exactamente 11 columnas");
            else {
                String type=v[2].toUpperCase(Locale.ROOT);
                if(!v[0].matches("[A-Za-z0-9]{8}")) reasons.add("código debe tener 8 caracteres alfanuméricos");
                if(v[1].isBlank()) reasons.add("nombre obligatorio");
                if(!List.of("MEDICAMENTO","UTENSILIO").contains(type)) reasons.add("tipo debe ser MEDICAMENTO o UTENSILIO");
                if("MEDICAMENTO".equals(type)&&v[10].isBlank()) reasons.add("Invima obligatorio para medicamento");
                if(v[3].isBlank()) reasons.add("lote obligatorio");
                if(!codes.add(v[0])) reasons.add("código duplicado en archivo");
                try { if(new java.math.BigDecimal(v[4]).signum()<0||new java.math.BigDecimal(v[5]).signum()<0||Integer.parseInt(v[6])<0||Integer.parseInt(v[7])<0) reasons.add("precio, cantidad o stock mínimo negativos"); LocalDate entry=LocalDate.parse(v[8]),expiry=LocalDate.parse(v[9]);if(entry.isAfter(LocalDate.now()))reasons.add("fecha de ingreso futura");if(expiry.isBefore(LocalDate.now())||expiry.isBefore(entry))reasons.add("fecha de vencimiento inválida o expirada"); }
                catch(Exception e) { reasons.add("precio, cantidad, stock mínimo o fecha inválidos"); }
                if(v[1].length()>120||v[3].length()>80) reasons.add("nombre o lote excede el máximo permitido");
            }
            if(reasons.isEmpty() && !db.queryForList("SELECT id FROM medicamento_inventario WHERE codigo=?",v[0]).isEmpty()) reasons.add("el código ya existe en inventario");
            if(reasons.isEmpty()) { Map<String,Object> item=new LinkedHashMap<>(); item.put("fila",r+1);item.put("codigo",v[0]);item.put("nombre",v[1]);item.put("tipo",v[2].toUpperCase(Locale.ROOT));item.put("lote",v[3]);item.put("precioCompra",new java.math.BigDecimal(v[4]));item.put("precioVenta",new java.math.BigDecimal(v[5]));item.put("cantidad",Integer.valueOf(v[6]));item.put("stockMinimo",Integer.valueOf(v[7]));item.put("fechaIngreso",v[8]);item.put("fechaVencimiento",v[9]);item.put("registroInvima",v[10]);validos.add(item); }
            else { error.put("motivo",String.join("; ",reasons)); errores.add(error); }
        }
        db.update("INSERT INTO importacion_inventario(nombre_archivo,usuario_id,total_filas,filas_validas,filas_error,estado) VALUES(?,?,?,?,?,'PREVIA')", file.getOriginalFilename(), usuario, rows.size()-1, validos.size(), errores.size());
        Number importacionId = db.queryForObject("SELECT LAST_INSERT_ID()", Number.class);
        for (Map<String,Object> error : errores) db.update("INSERT INTO importacion_inventario_error(importacion_id,fila,motivo) VALUES(?,?,?)", importacionId, error.get("fila"), error.get("motivo"));
        for (Map<String,Object> item : validos) db.update("INSERT INTO importacion_inventario_detalle(importacion_id,fila,datos) VALUES(?,?,?)",importacionId,item.get("fila"),json.writeValueAsString(item));
        return Map.of("importacionId",importacionId,"totalFilas",rows.size()-1,"validos",validos,"errores",errores,"puedeConfirmar",!validos.isEmpty());
    }

    @PostMapping("/inventario/importar/confirmar") @Transactional
    public Map<String,Object> confirmarImport(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long usuario,@RequestBody Map<String,Object> request) throws IOException {
        requireRole(role,"ADMINISTRADOR");
        if(request.get("importacionId")==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Se requiere importacionId de una previsualización vigente");
        long importacionId=((Number)request.get("importacionId")).longValue();
        Map<String,Object> preview=db.queryForMap("SELECT estado,usuario_id,filas_error FROM importacion_inventario WHERE id=? FOR UPDATE",importacionId);
        if(!Objects.equals(((Number)preview.get("usuario_id")).longValue(),usuario)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"La previsualización pertenece a otro usuario");
        if(!"PREVIA".equals(preview.get("estado"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"La previsualización ya fue confirmada o cancelada");
        List<Map<String,Object>> stored=db.queryForList("SELECT fila,datos FROM importacion_inventario_detalle WHERE importacion_id=? ORDER BY fila",importacionId);
        if(stored.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"La previsualización no contiene filas válidas");
        int imported=0;
        for(Map<String,Object> row:stored){ Map<String,Object> item=json.readValue(String.valueOf(row.get("datos")),Map.class); validarProducto(item); if(!db.queryForList("SELECT id FROM medicamento_inventario WHERE codigo=?",item.get("codigo")).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"El código "+item.get("codigo")+" ya existe; no se aplicó la importación"); crearProductoInterno(item,usuario); imported++; }
        db.update("UPDATE importacion_inventario SET estado='CONFIRMADA' WHERE id=?",importacionId);
        return Map.of("importacionId",importacionId,"importados",imported,"fecha",new Date());
    }

    @GetMapping("/despachos/pendientes")
    public List<Map<String,Object>> despachos(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role) {
        requireRole(role, "DESPACHO_MEDICO");
        return db.queryForList("SELECT f.formula_id,d.id despacho_id,f.fecha_emision,f.estado,f.dosis,f.unidad_dosis,f.frecuencia_horas,f.duracion_dias,f.via_administracion,f.indicaciones,COALESCE(f.cantidad_prescrita,1) cantidad_prescrita,COALESCE(f.cantidad_prescrita,1)-COALESCE((SELECT SUM(dd.cantidad_entregada) FROM despacho_detalle dd JOIN despacho_medicamento dx ON dx.id=dd.despacho_id WHERE dx.formula_id=f.formula_id),0) cantidad_pendiente,m.nombre medicamento,ma.nombre_mascota mascota,c.nombres cliente,COALESCE(i.stock,0) stock_disponible,d.estado estado_despacho FROM formula_medica f JOIN medicamento m ON m.medicamento_id=f.medicamento_id JOIN mascota ma ON ma.mascota_id=f.mascota_id JOIN cliente c ON c.cliente_id=ma.cliente_id LEFT JOIN (SELECT mi.medicamento_id,SUM(CASE WHEN l.fecha_vencimiento>=CURDATE() THEN l.cantidad ELSE 0 END) stock FROM medicamento_inventario mi LEFT JOIN lote_medicamento l ON l.inventario_id=mi.id WHERE mi.activo=1 GROUP BY mi.medicamento_id) i ON i.medicamento_id=f.medicamento_id LEFT JOIN despacho_medicamento d ON d.formula_id=f.formula_id WHERE f.estado='ACTIVA' AND COALESCE(d.estado,'PENDIENTE_DESPACHO') IN ('PENDIENTE_DESPACHO','EN_PREPARACION','DESPACHO_PARCIAL','PENDIENTE_DISPONIBILIDAD') ORDER BY f.fecha_emision");
    }

    @GetMapping("/despachos/historial")
    public List<Map<String,Object>> historialDespachos(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role) {
        requireRole(role, "DESPACHO_MEDICO");
        return db.queryForList("SELECT d.id despacho_id,d.formula_id,d.estado,d.entregado_en,u.username usuario,f.fecha_emision,ma.nombre_mascota mascota,cl.nombres propietario,md.nombres medico,med.nombre medicamento,dd.cantidad_entregada,l.numero_lote,l.fecha_vencimiento FROM despacho_medicamento d JOIN usuario u ON u.id=d.usuario_id JOIN formula_medica f ON f.formula_id=d.formula_id JOIN mascota ma ON ma.mascota_id=f.mascota_id JOIN cliente cl ON cl.cliente_id=ma.cliente_id JOIN medico md ON md.id=f.medico_id JOIN despacho_detalle dd ON dd.despacho_id=d.id JOIN medicamento_inventario mi ON mi.id=dd.medicamento_id JOIN medicamento med ON med.medicamento_id=mi.medicamento_id JOIN lote_medicamento l ON l.id=dd.lote_id ORDER BY d.entregado_en DESC,d.id DESC");
    }

    @GetMapping("/despachos/{despachoId}/historial")
    public List<Map<String,Object>> historialEstadosDespacho(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@PathVariable long despachoId){
        requireRole(role,"DESPACHO_MEDICO");
        return db.queryForList("SELECT l.estado_anterior,l.estado_nuevo,l.fecha,l.observacion,u.username usuario FROM despacho_log l JOIN usuario u ON u.id=l.usuario_id WHERE l.despacho_id=? ORDER BY l.fecha,l.id",despachoId);
    }

    @GetMapping("/despachos/{formulaId}/lotes")
    public List<Map<String,Object>> lotesDespacho(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@PathVariable long formulaId){
        requireRole(role,"DESPACHO_MEDICO");
        Map<String,Object> f=db.queryForMap("SELECT medicamento_id FROM formula_medica WHERE formula_id=? AND estado='ACTIVA'",formulaId);
        return db.queryForList("SELECT i.id inventario_id,i.codigo,m.nombre medicamento,l.id lote_id,l.numero_lote,l.fecha_ingreso,l.fecha_vencimiento,l.cantidad,CASE WHEN l.fecha_vencimiento<CURDATE() THEN 'VENCIDO' WHEN l.fecha_vencimiento<=DATE_ADD(CURDATE(),INTERVAL 30 DAY) THEN 'PROXIMO_A_VENCER' ELSE 'VIGENTE' END estado_lote FROM medicamento_inventario i JOIN medicamento m ON m.medicamento_id=i.medicamento_id JOIN lote_medicamento l ON l.inventario_id=i.id WHERE i.medicamento_id=? AND i.activo=1 ORDER BY (l.fecha_vencimiento<CURDATE()),l.fecha_vencimiento,l.id",f.get("medicamento_id"));
    }

    @PutMapping("/despachos/{formulaId}/preparar") @Transactional
    public Map<String,Object> prepararDespacho(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long usuario,@PathVariable long formulaId){
        requireRole(role,"DESPACHO_MEDICO");
        bloquearCitaDeFormula(formulaId);
        if(db.queryForList("SELECT formula_id FROM formula_medica WHERE formula_id=? AND estado='ACTIVA' FOR UPDATE",formulaId).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Fórmula activa no encontrada");
        List<Map<String,Object>> rows=db.queryForList("SELECT id,estado FROM despacho_medicamento WHERE formula_id=? FOR UPDATE",formulaId);long id;String previous;
        if(rows.isEmpty()){db.update("INSERT INTO despacho_medicamento(formula_id,usuario_id,estado) VALUES(?,?,'PENDIENTE_DESPACHO')",formulaId,usuario);id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);previous="PENDIENTE_DESPACHO";}else{id=((Number)rows.get(0).get("id")).longValue();previous=String.valueOf(rows.get(0).get("estado"));}
        if("EN_PREPARACION".equals(previous))return Map.of("despachoId",id,"formulaId",formulaId,"estado",previous,"repetida",true);
        if(!List.of("PENDIENTE_DESPACHO","PENDIENTE_DISPONIBILIDAD","DESPACHO_PARCIAL").contains(previous))throw new ResponseStatusException(HttpStatus.CONFLICT,"El despacho ya está finalizado o cancelado");
        db.update("UPDATE despacho_medicamento SET estado='EN_PREPARACION',usuario_id=? WHERE id=?",usuario,id);db.update("INSERT INTO despacho_log(despacho_id,estado_anterior,estado_nuevo,usuario_id,observacion) VALUES(?,?,?,?,?)",id,previous,"EN_PREPARACION",usuario,"Inicio de preparación");
        return Map.of("despachoId",id,"formulaId",formulaId,"estado","EN_PREPARACION");
    }

    @PutMapping("/despachos/{formulaId}/cancelar") @Transactional
    public Map<String,Object> cancelarDespacho(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long usuario,@PathVariable long formulaId,@RequestBody Map<String,Object> body){
        requireRole(role,"ADMINISTRADOR");String reason=String.valueOf(body.getOrDefault("motivo","")).trim();if(reason.isBlank()||reason.length()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Motivo de cancelación obligatorio (máximo 500 caracteres)");
        if(db.queryForList("SELECT formula_id FROM formula_medica WHERE formula_id=?",formulaId).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Fórmula no encontrada");
        List<Map<String,Object>> rows=db.queryForList("SELECT id,estado FROM despacho_medicamento WHERE formula_id=? FOR UPDATE",formulaId);long id;String previous;
        if(rows.isEmpty()){db.update("INSERT INTO despacho_medicamento(formula_id,usuario_id,estado) VALUES(?,?,'PENDIENTE_DESPACHO')",formulaId,usuario);id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);previous="PENDIENTE_DESPACHO";}else{id=((Number)rows.get(0).get("id")).longValue();previous=String.valueOf(rows.get(0).get("estado"));}
        if("CANCELADO".equals(previous))return Map.of("despachoId",id,"estado","CANCELADO","repetida",true);
        if(List.of("DESPACHADO","DESPACHO_PARCIAL").contains(previous)||!db.queryForList("SELECT id FROM despacho_detalle WHERE despacho_id=? AND cantidad_entregada>0",id).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Una entrega iniciada o confirmada requiere procedimiento de reversión; no se puede cancelar directamente");
        db.update("UPDATE despacho_medicamento SET estado='CANCELADO',usuario_id=? WHERE id=?",usuario,id);db.update("INSERT INTO despacho_log(despacho_id,estado_anterior,estado_nuevo,usuario_id,observacion) VALUES(?,?,?,?,?)",id,previous,"CANCELADO",usuario,reason);
        return Map.of("despachoId",id,"estado","CANCELADO","message","Cancelación autorizada y auditada");
    }

    @PostMapping("/despachos/{formulaId}/confirmar") @Transactional
    public Map<String,Object> confirmarDespacho(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long usuario, @PathVariable long formulaId,
            @RequestBody Map<String,Object> request) {
        requireRole(role,"DESPACHO_MEDICO");
        Object rawQuantity = request.get("cantidad");
        int requested;
        try { requested = Integer.parseInt(String.valueOf(rawQuantity)); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La cantidad debe ser un entero positivo"); }
        if (requested <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La cantidad debe ser mayor que cero");
        String operationKey = String.valueOf(request.getOrDefault("operacionId", "")).trim();
        if (operationKey.isBlank() || operationKey.length() > 80) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Se requiere operacionId (máximo 80 caracteres)");
        bloquearCitaDeFormula(formulaId);

        Map<String,Object> formula = db.query("SELECT formula_id,medicamento_id,COALESCE(cantidad_prescrita,1) cantidad_prescrita FROM formula_medica WHERE formula_id=? AND estado='ACTIVA' FOR UPDATE",
                rs -> rs.next() ? Map.of("formula_id",rs.getLong("formula_id"),"medicamento_id",rs.getLong("medicamento_id"),"cantidad_prescrita",rs.getInt("cantidad_prescrita")) : null, formulaId);
        if (formula == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Fórmula activa no encontrada");
        List<Map<String,Object>> dispatchRows = db.queryForList("SELECT id,estado FROM despacho_medicamento WHERE formula_id=? FOR UPDATE",formulaId);
        Long dispatchId;
        String previousState;
        if (dispatchRows.isEmpty()) {
            db.update("INSERT INTO despacho_medicamento(formula_id,usuario_id,estado) VALUES(?,?,'PENDIENTE_DESPACHO')",formulaId,usuario);
            dispatchId = db.queryForObject("SELECT id FROM despacho_medicamento WHERE formula_id=?",Long.class,formulaId);
            previousState = "PENDIENTE_DESPACHO";
        } else {
            dispatchId = ((Number)dispatchRows.get(0).get("id")).longValue();
            previousState = String.valueOf(dispatchRows.get(0).get("estado"));
        }
        List<Map<String,Object>> duplicateOperation = db.queryForList("SELECT id FROM despacho_operacion WHERE despacho_id=? AND operacion_id=?",dispatchId,operationKey);
        if (!duplicateOperation.isEmpty()) return Map.of("formulaId",formulaId,"despachoId",dispatchId,"estado",previousState,"repetida",true,"message","La operación ya había sido procesada");
        if ("DESPACHADO".equals(previousState) || "CANCELADO".equals(previousState)) throw new ResponseStatusException(HttpStatus.CONFLICT,"El despacho ya está finalizado o cancelado");
        int prescribed = ((Number)formula.get("cantidad_prescrita")).intValue();
        Number deliveredSoFarValue = db.queryForObject("SELECT COALESCE(SUM(cantidad_entregada),0) FROM despacho_detalle WHERE despacho_id=?",Number.class,dispatchId);
        int deliveredSoFar = deliveredSoFarValue == null ? 0 : deliveredSoFarValue.intValue();
        int remainingPrescription = prescribed - deliveredSoFar;
        if (remainingPrescription <= 0) throw new ResponseStatusException(HttpStatus.CONFLICT,"La cantidad prescrita ya fue dispensada");
        if (requested > remainingPrescription) throw new ResponseStatusException(HttpStatus.CONFLICT,"La cantidad supera el saldo pendiente de la fórmula: " + remainingPrescription);
        List<Map<String,Object>> lots = db.queryForList("SELECT l.id,l.inventario_id,l.cantidad,l.fecha_vencimiento FROM lote_medicamento l JOIN medicamento_inventario i ON i.id=l.inventario_id WHERE i.medicamento_id=? AND i.activo=1 AND l.fecha_vencimiento>=CURDATE() AND l.cantidad>0 ORDER BY l.fecha_vencimiento,l.id FOR UPDATE",formula.get("medicamento_id"));
        int available = lots.stream().mapToInt(l -> ((Number)l.get("cantidad")).intValue()).sum();
        if (requested > available) throw new ResponseStatusException(HttpStatus.CONFLICT,"Existencia vigente insuficiente; disponible: " + available + ". Registre una cantidad parcial.");

        db.update("INSERT INTO despacho_operacion(despacho_id,operacion_id,usuario_id,cantidad,fecha) VALUES(?,?,?,?,NOW())",dispatchId,operationKey,usuario,requested);
        int left = requested;
        for (Map<String,Object> lot : lots) {
            if (left == 0) break;
            int lotAvailable = ((Number)lot.get("cantidad")).intValue();
            int allocation = Math.min(left, lotAvailable);
            if (allocation == 0) continue;
            int changed = db.update("UPDATE lote_medicamento SET cantidad=cantidad-? WHERE id=? AND cantidad>=? AND fecha_vencimiento>=CURDATE()",allocation,lot.get("id"),allocation);
            if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,"El lote cambió durante la operación; vuelva a consultar existencias");
            long inventoryId=((Number)lot.get("inventario_id")).longValue();
            db.update("INSERT INTO despacho_detalle(despacho_id,medicamento_id,lote_id,cantidad_prescrita,cantidad_entregada) VALUES(?,?,?,?,?)",dispatchId,inventoryId,lot.get("id"),allocation,allocation);
            db.update("INSERT INTO movimiento_inventario(inventario_id,lote_id,tipo,cantidad,formula_id,usuario_id,referencia,despacho_id) VALUES(?,?,'SALIDA',?,?,?,?,?)",inventoryId,lot.get("id"),allocation,formulaId,usuario,"DESPACHO:"+operationKey,dispatchId);
            left -= allocation;
        }
        int newTotal = deliveredSoFar + requested;
        String state = newTotal >= prescribed ? "DESPACHADO" : "DESPACHO_PARCIAL";
        db.update("UPDATE despacho_medicamento SET estado=?,entregado_en=NOW(),usuario_id=? WHERE id=?",state,usuario,dispatchId);
        db.update("INSERT INTO despacho_log(despacho_id,estado_anterior,estado_nuevo,usuario_id,observacion) VALUES(?,?,?,?,?)",dispatchId,previousState,state,usuario,"Operación "+operationKey+": entregadas "+requested+" unidades");
        return Map.of("formulaId",formulaId,"despachoId",dispatchId,"cantidadEntregada",requested,"cantidadPendiente",prescribed-newTotal,"estado",state);
    }

    @GetMapping("/facturas/citas-atendidas")
    public List<Map<String,Object>> citasAtendidasSinFactura(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role) {
        requireRole(role,"ADMINISTRADOR","SECRETARIA");
        return db.queryForList("SELECT c.id cita_id,c.fecha,ma.nombre_mascota mascota,CONCAT(cl.nombres,' ',cl.apellidos) propietario,CONCAT(md.nombres,' ',md.apellidos) veterinario,e.nombre especialidad,md.especializacion_id FROM citas c JOIN mascota ma ON ma.mascota_id=c.mascota_id JOIN cliente cl ON cl.cliente_id=ma.cliente_id JOIN medico md ON md.id=c.medico_id LEFT JOIN especializacion e ON e.id=md.especializacion_id WHERE c.estado='ATENDIDA' AND NOT EXISTS (SELECT 1 FROM factura f WHERE f.cita_id=c.id) AND NOT EXISTS (SELECT 1 FROM formula_medica f LEFT JOIN despacho_medicamento d ON d.formula_id=f.formula_id WHERE f.cita_id=c.id AND ((d.id IS NULL AND f.estado='ACTIVA') OR d.estado IN ('PENDIENTE_DESPACHO','PENDIENTE_DISPONIBILIDAD','EN_PREPARACION','DESPACHO_PARCIAL'))) ORDER BY c.fecha DESC LIMIT 500");
    }

    @GetMapping("/facturas")
    public List<Map<String,Object>> listarFacturas(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@RequestParam(defaultValue="") String texto) {
        requireRole(role,"ADMINISTRADOR","SECRETARIA");String q="%"+texto.trim()+"%";
        return db.queryForList("SELECT f.id factura_id,f.cita_id,c.fecha fecha_atencion,CONCAT(cl.nombres,' ',cl.apellidos) propietario,ma.nombre_mascota mascota,CONCAT(md.nombres,' ',md.apellidos) veterinario,e.nombre especialidad,f.subtotal,f.iva,f.descuento,f.total,f.estado,COALESCE(p.pagado,0) pagado,CASE WHEN f.estado='ANULADA' THEN 0 ELSE f.total-COALESCE(p.pagado,0) END saldo FROM factura f JOIN citas c ON c.id=f.cita_id JOIN cliente cl ON cl.cliente_id=f.cliente_id JOIN mascota ma ON ma.mascota_id=f.mascota_id JOIN medico md ON md.id=c.medico_id LEFT JOIN especializacion e ON e.id=md.especializacion_id LEFT JOIN (SELECT factura_id,SUM(valor) pagado FROM pago_factura GROUP BY factura_id) p ON p.factura_id=f.id WHERE CAST(f.id AS CHAR) LIKE ? OR CAST(f.cita_id AS CHAR) LIKE ? OR CONCAT(cl.nombres,' ',cl.apellidos) LIKE ? OR ma.nombre_mascota LIKE ? ORDER BY f.id DESC LIMIT 200",q,q,q,q);
    }

    @GetMapping("/facturas/{id}/pagos")
    public List<Map<String,Object>> listarPagosFactura(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@PathVariable long id) {
        requireRole(role,"ADMINISTRADOR","SECRETARIA");
        return db.queryForList("SELECT p.id,p.factura_id,p.valor,p.medio,p.usuario_id,u.username usuario,p.pagado_en,p.operacion_id,p.tipo,p.pago_original_id,p.motivo_reversion FROM pago_factura p JOIN usuario u ON u.id=p.usuario_id WHERE p.factura_id=? ORDER BY p.id",id);
    }

    @PostMapping("/facturas") @Transactional
    public Map<String,Object> crearFactura(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long usuario,@RequestBody Map<String,Object> body) {
        requireRole(role,"ADMINISTRADOR","SECRETARIA");
        if(body.get("citaId")==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Se requiere citaId");
        long cita=((Number)body.get("citaId")).longValue();
        List<Map<String,Object>> attended=db.queryForList("SELECT c.id,c.mascota_id,ma.cliente_id,md.especializacion_id FROM citas c JOIN mascota ma ON ma.mascota_id=c.mascota_id JOIN medico md ON md.id=c.medico_id WHERE c.id=? AND c.estado='ATENDIDA' FOR UPDATE",cita);
        if(attended.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Solo se puede facturar una cita atendida");
        Map<String,Object> c=attended.get(0);
        if(!db.queryForList("SELECT id FROM factura WHERE cita_id=?",cita).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"La cita ya tiene factura");
        Number pendientes=db.queryForObject("SELECT COUNT(*) FROM formula_medica f LEFT JOIN despacho_medicamento d ON d.formula_id=f.formula_id WHERE f.cita_id=? AND ((d.id IS NULL AND f.estado='ACTIVA') OR d.estado IN ('PENDIENTE_DESPACHO','PENDIENTE_DISPONIBILIDAD','EN_PREPARACION','DESPACHO_PARCIAL'))",Number.class,cita);
        if(pendientes!=null&&pendientes.intValue()>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Complete o cancele los despachos de las fórmulas activas antes de facturar; así se evita cobrar solo una entrega parcial");
        List<Map<String,Object>> tariffs;
        if(body.get("tarifaId")!=null){
            long tarifaId;try{tarifaId=Long.parseLong(String.valueOf(body.get("tarifaId")));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"tarifaId inválido");}
            tariffs=db.queryForList("SELECT id,servicio,valor,iva_porcentaje FROM tarifa_servicio WHERE id=? AND activa=1 AND vigente_desde<=CURDATE() AND (vigente_hasta IS NULL OR vigente_hasta>=CURDATE()) AND (especializacion_id=? OR especializacion_id IS NULL)",tarifaId,c.get("especializacion_id"));
        } else {
            tariffs=db.queryForList("SELECT id,servicio,valor,iva_porcentaje FROM tarifa_servicio WHERE activa=1 AND servicio='Consulta veterinaria' AND vigente_desde<=CURDATE() AND (vigente_hasta IS NULL OR vigente_hasta>=CURDATE()) AND (especializacion_id=? OR especializacion_id IS NULL) ORDER BY (especializacion_id IS NULL),vigente_desde DESC,id DESC LIMIT 1",c.get("especializacion_id"));
        }
        if(tariffs.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Seleccione una tarifa vigente de la especialidad correspondiente; por defecto se usa 'Consulta veterinaria'");
        Map<String,Object> tariff=tariffs.get(0);
        java.math.BigDecimal subtotal=new java.math.BigDecimal(String.valueOf(tariff.get("valor"))).setScale(2,java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal serviceRate=new java.math.BigDecimal(String.valueOf(tariff.get("iva_porcentaje"))).setScale(2,java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal tax=subtotal.multiply(serviceRate).divide(new java.math.BigDecimal("100"),2,java.math.RoundingMode.HALF_UP);
        List<Map<String,Object>> products=db.queryForList("SELECT i.id,m.nombre,i.precio_venta,i.iva_porcentaje,SUM(dd.cantidad_entregada) cantidad FROM formula_medica f JOIN despacho_medicamento d ON d.formula_id=f.formula_id JOIN despacho_detalle dd ON dd.despacho_id=d.id JOIN medicamento_inventario i ON i.id=dd.medicamento_id JOIN medicamento m ON m.medicamento_id=i.medicamento_id WHERE f.cita_id=? AND d.estado IN ('DESPACHADO','DESPACHO_PARCIAL') AND dd.cantidad_entregada>0 GROUP BY i.id,m.nombre,i.precio_venta,i.iva_porcentaje ORDER BY m.nombre",cita);
        for(Map<String,Object> product:products){java.math.BigDecimal unit=new java.math.BigDecimal(String.valueOf(product.get("precio_venta"))).setScale(2,java.math.RoundingMode.HALF_UP);java.math.BigDecimal productRate=new java.math.BigDecimal(String.valueOf(product.get("iva_porcentaje"))).setScale(2,java.math.RoundingMode.HALF_UP);int quantity=((Number)product.get("cantidad")).intValue();java.math.BigDecimal line=unit.multiply(java.math.BigDecimal.valueOf(quantity));subtotal=subtotal.add(line);tax=tax.add(line.multiply(productRate).divide(new java.math.BigDecimal("100"),2,java.math.RoundingMode.HALF_UP));}
        java.math.BigDecimal discount=java.math.BigDecimal.ZERO;
        String discountReason=String.valueOf(body.getOrDefault("motivoDescuento","" )).trim();
        if(body.get("descuento")!=null){try{discount=new java.math.BigDecimal(String.valueOf(body.get("descuento"))).setScale(2,java.math.RoundingMode.HALF_UP);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Descuento inválido");}if(discount.signum()<0||discount.compareTo(subtotal)>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El descuento no puede ser negativo ni superar el subtotal");if(discount.signum()>0&&(!"ADMINISTRADOR".equalsIgnoreCase(role)||discountReason.isBlank()||discountReason.length()>500))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"El descuento requiere autorización de administrador y motivo (máximo 500 caracteres)");}
        java.math.BigDecimal total=subtotal.add(tax).subtract(discount);
        db.update("INSERT INTO factura(cita_id,cliente_id,mascota_id,subtotal,iva,descuento,total) VALUES(?,?,?,?,?,?,?)",cita,c.get("cliente_id"),c.get("mascota_id"),subtotal,tax,discount,total);
        long invoice=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        db.update("INSERT INTO factura_detalle(factura_id,concepto,cantidad,precio_unitario,iva_porcentaje) VALUES(?,?,1,?,?)",invoice,tariff.get("servicio"),tariff.get("valor"),serviceRate);
        db.update("INSERT INTO factura_tarifa_snapshot(factura_id,servicio,tarifa,iva_porcentaje) VALUES(?,?,?,?)",invoice,tariff.get("servicio"),tariff.get("valor"),serviceRate);
        for(Map<String,Object> product:products)db.update("INSERT INTO factura_detalle(factura_id,concepto,cantidad,precio_unitario,iva_porcentaje) VALUES(?,?,?,?,?)",invoice,"Medicamento dispensado: "+product.get("nombre"),product.get("cantidad"),product.get("precio_venta"),product.get("iva_porcentaje"));
        db.update("INSERT INTO factura_log(factura_id,accion,usuario_id,detalle) VALUES(?,'CREADA',?,?)",invoice,usuario,"Calculada con tarifa vigente #"+tariff.get("id")+" ("+tariff.get("servicio")+")"+(discount.signum()>0?"; descuento autorizado "+discount+"; motivo: "+discountReason:""));
        return Map.of("facturaId",invoice,"subtotal",subtotal,"iva",tax,"descuento",discount,"total",total,"servicio",tariff.get("servicio"),"message","Factura calculada con tarifa vigente; solo incluye medicamentos efectivamente dispensados");
    }
    @PutMapping("/facturas/{id}/anular") @Transactional
    public Map<String,Object> anularFactura(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long user,@PathVariable long id,@RequestBody Map<String,Object> body){
        requireRole(role,"ADMINISTRADOR");String reason=String.valueOf(body.getOrDefault("motivo","")).trim();if(reason.isBlank()||reason.length()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Motivo obligatorio (máximo 500 caracteres)");
        List<Map<String,Object>> rows=db.queryForList("SELECT estado FROM factura WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Factura no encontrada");if("ANULADA".equals(rows.get(0).get("estado")))return Map.of("facturaId",id,"repetida",true,"estado","ANULADA");
        java.math.BigDecimal netPaid=pagoNetoBloqueado(id);if(netPaid.signum()!=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Primero debe reversar los pagos hasta que el saldo neto quede en cero; el historial no se elimina");
        db.update("UPDATE factura SET estado='ANULADA' WHERE id=?",id);db.update("INSERT INTO factura_log(factura_id,accion,usuario_id,detalle) VALUES(?,'ANULADA',?,?)",id,user,reason);return Map.of("facturaId",id,"estado","ANULADA","message","Factura anulada de forma trazable");
    }

    @PostMapping("/facturas/{id}/pagos") @Transactional
    public Map<String,Object> pago(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long user,@PathVariable long id,@RequestBody Map<String,Object> body) {
        requireRole(role,"ADMINISTRADOR","SECRETARIA");
        String operation=String.valueOf(body.getOrDefault("operacionId","")).trim();if(operation.isBlank()||operation.length()>80)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Se requiere operacionId para evitar pagos duplicados");
        java.math.BigDecimal value;try{value=new java.math.BigDecimal(String.valueOf(body.get("valor"))).setScale(2,java.math.RoundingMode.HALF_UP);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valor de pago inválido");}
        if(value.signum()<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El pago debe ser mayor que cero");
        String medio=String.valueOf(body.getOrDefault("medio","EFECTIVO")).toUpperCase(Locale.ROOT);if(!List.of("EFECTIVO","TARJETA","TRANSFERENCIA","PSE").contains(medio))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Medio de pago no permitido");
        List<Map<String,Object>> invoices=db.queryForList("SELECT total,estado FROM factura WHERE id=? FOR UPDATE",id);if(invoices.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Factura no encontrada");
        List<Map<String,Object>> repeat=db.queryForList("SELECT id,factura_id,valor,tipo,medio FROM pago_factura WHERE operacion_id=? FOR UPDATE",operation);
        if(!repeat.isEmpty()){Map<String,Object> r=repeat.get(0);java.math.BigDecimal stored=new java.math.BigDecimal(String.valueOf(r.get("valor")));if(((Number)r.get("factura_id")).longValue()!=id||!"PAGO".equals(r.get("tipo"))||stored.compareTo(value)!=0||!medio.equals(String.valueOf(r.get("medio"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"La clave idempotente ya se usó con otra transacción o un contenido diferente");return Map.of("facturaId",id,"pagoId",r.get("id"),"repetido",true,"message","El pago ya fue registrado");}
        if("ANULADA".equals(invoices.get(0).get("estado")))throw new ResponseStatusException(HttpStatus.CONFLICT,"No se reciben pagos de una factura anulada");
        java.math.BigDecimal total=new java.math.BigDecimal(String.valueOf(invoices.get(0).get("total"))),paid=pagoNetoBloqueado(id),after=paid.add(value);
        if(after.compareTo(total)>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El pago supera el saldo pendiente de "+total.subtract(paid));
        db.update("INSERT INTO pago_factura(factura_id,valor,medio,usuario_id,operacion_id) VALUES(?,?,?,?,?)",id,value,medio,user,operation);
        String state=after.compareTo(total)==0?"PAGADA":"PARCIAL";db.update("UPDATE factura SET estado=? WHERE id=?",state,id);
        db.update("INSERT INTO factura_log(factura_id,accion,usuario_id,detalle) VALUES(?,'PAGO',?,?)",id,user,"Pago "+value+" por "+medio+"; clave "+operation);
        return Map.of("facturaId",id,"pagado",after,"saldo",total.subtract(after),"estado",state);
    }

    @PostMapping("/facturas/{id}/pagos/{pagoId}/reversiones") @Transactional
    public Map<String,Object> reversarPago(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-User-Id") long user,@PathVariable long id,@PathVariable long pagoId,@RequestBody Map<String,Object> body) {
        requireRole(role,"ADMINISTRADOR");
        String operation=String.valueOf(body.getOrDefault("operacionId","")).trim(),reason=String.valueOf(body.getOrDefault("motivo","")).trim(),medio=String.valueOf(body.getOrDefault("medio","EFECTIVO")).toUpperCase(Locale.ROOT);
        if(operation.isBlank()||operation.length()>80)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Se requiere operacionId para evitar reversiones duplicadas");
        if(reason.isBlank()||reason.length()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El motivo de reversión es obligatorio (máximo 500 caracteres)");
        if(!List.of("EFECTIVO","TARJETA","TRANSFERENCIA","PSE").contains(medio))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Medio de reversión no permitido");
        java.math.BigDecimal value;try{value=new java.math.BigDecimal(String.valueOf(body.get("valor"))).setScale(2,java.math.RoundingMode.HALF_UP);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valor de reversión inválido");}
        if(value.signum()<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La reversión debe ser mayor que cero");
        List<Map<String,Object>> invoices=db.queryForList("SELECT total,estado FROM factura WHERE id=? FOR UPDATE",id);if(invoices.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Factura no encontrada");
        List<Map<String,Object>> repeat=db.queryForList("SELECT id,factura_id,valor,tipo,pago_original_id,medio,motivo_reversion FROM pago_factura WHERE operacion_id=? FOR UPDATE",operation);
        if(!repeat.isEmpty()){Map<String,Object> r=repeat.get(0);java.math.BigDecimal stored=new java.math.BigDecimal(String.valueOf(r.get("valor")));if(((Number)r.get("factura_id")).longValue()!=id||!"REVERSO".equals(r.get("tipo"))||((Number)r.get("pago_original_id")).longValue()!=pagoId||stored.compareTo(value.negate())!=0||!medio.equals(String.valueOf(r.get("medio")))||!reason.equals(String.valueOf(r.get("motivo_reversion"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"La clave idempotente ya se usó con otra transacción o un contenido diferente");return Map.of("facturaId",id,"reversionId",r.get("id"),"repetida",true,"message","La reversión ya fue registrada");}
        if("ANULADA".equals(invoices.get(0).get("estado")))throw new ResponseStatusException(HttpStatus.CONFLICT,"No se puede reversar el pago de una factura anulada");
        List<Map<String,Object>> originals=db.queryForList("SELECT id,valor,tipo FROM pago_factura WHERE id=? AND factura_id=? FOR UPDATE",pagoId,id);if(originals.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Pago original no encontrado en esta factura");
        Map<String,Object> original=originals.get(0);if(!"PAGO".equals(original.get("tipo")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Solo se puede reversar un pago original");
        java.math.BigDecimal originalValue=new java.math.BigDecimal(String.valueOf(original.get("valor"))),reversed=reversionesBloqueadas(pagoId),remaining=originalValue.subtract(reversed);
        if(value.compareTo(remaining)>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La reversión supera el monto aún disponible del pago: "+remaining);
        db.update("INSERT INTO pago_factura(factura_id,valor,medio,usuario_id,operacion_id,tipo,pago_original_id,motivo_reversion) VALUES(?,?,?,?,?,'REVERSO',?,?)",id,value.negate(),medio,user,operation,pagoId,reason);
        long reversalId=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);java.math.BigDecimal total=new java.math.BigDecimal(String.valueOf(invoices.get(0).get("total"))),paid=pagoNetoBloqueado(id);
        String state=paid.compareTo(total)>=0?"PAGADA":paid.signum()>0?"PARCIAL":"PENDIENTE";db.update("UPDATE factura SET estado=? WHERE id=?",state,id);
        db.update("INSERT INTO factura_log(factura_id,accion,usuario_id,detalle) VALUES(?,'REVERSION_PAGO',?,?)",id,user,"Reversión #"+reversalId+" del pago #"+pagoId+" por "+value+" vía "+medio+"; motivo: "+reason+"; clave "+operation);
        return Map.of("facturaId",id,"reversionId",reversalId,"reembolsado",value,"pagadoNeto",paid,"saldo",total.subtract(paid),"estado",state);
    }

    @GetMapping("/reportes/{tipo}") public ResponseEntity<byte[]> reporte(@PathVariable String tipo,@RequestParam(defaultValue="xlsx") String formato) throws Exception { String title="Reporte "+tipo+" - Clínica Veterinaria"; if("pdf".equalsIgnoreCase(formato)){ByteArrayOutputStream out=new ByteArrayOutputStream(); Document doc=new Document(); PdfWriter.getInstance(doc,out); doc.open(); doc.add(new Paragraph(title)); for(Map<String,Object> row: datosReporte(tipo)) doc.add(new Paragraph(row.toString())); doc.close(); return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=reporte.pdf").body(out.toByteArray());} try(Workbook wb=new XSSFWorkbook()){Sheet sh=wb.createSheet("Reporte"); int r=0; sh.createRow(r++).createCell(0).setCellValue(title); for(Map<String,Object> row:datosReporte(tipo)){Row line=sh.createRow(r++); int c=0; for(Object v:row.values()) line.createCell(c++).setCellValue(String.valueOf(v));} ByteArrayOutputStream out=new ByteArrayOutputStream(); wb.write(out); return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=reporte.xlsx").body(out.toByteArray());} }
    private List<Map<String,Object>> datosReporte(String tipo){ if(tipo.equalsIgnoreCase("inventario")) return inventario("ADMINISTRADOR",""); return db.queryForList("SELECT id,fecha,motivo,estado FROM citas ORDER BY fecha DESC"); }
    private void bloquearCitaDeFormula(long formulaId) {
        List<Map<String,Object>> links=db.queryForList("SELECT cita_id FROM formula_medica WHERE formula_id=?",formulaId);
        if(links.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Fórmula no encontrada");
        Object raw=links.get(0).get("cita_id");if(!(raw instanceof Number n))throw new ResponseStatusException(HttpStatus.CONFLICT,"La fórmula no está vinculada a una cita; no puede dispensarse ni facturarse");
        long citaId=n.longValue();if(db.queryForList("SELECT id FROM citas WHERE id=? FOR UPDATE",citaId).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cita de la fórmula no encontrada");
        if(!db.queryForList("SELECT id FROM factura WHERE cita_id=?",citaId).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"No se puede preparar ni dispensar una fórmula después de facturar la cita");
    }

    private java.math.BigDecimal pagoNetoBloqueado(long facturaId) {
        List<Map<String,Object>> rows=db.queryForList("SELECT valor FROM pago_factura WHERE factura_id=? FOR UPDATE",facturaId);
        return rows.stream().map(r->new java.math.BigDecimal(String.valueOf(r.get("valor")))).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
    }

    private java.math.BigDecimal reversionesBloqueadas(long pagoId) {
        List<Map<String,Object>> rows=db.queryForList("SELECT valor FROM pago_factura WHERE pago_original_id=? AND tipo='REVERSO' FOR UPDATE",pagoId);
        return rows.stream().map(r->new java.math.BigDecimal(String.valueOf(r.get("valor"))).negate()).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
    }

    private Number findNumber(String sql, Object... args) { List<Map<String,Object>> rows = db.queryForList(sql, args); return rows.isEmpty() ? null : (Number) rows.get(0).values().iterator().next(); }
    private void validarProducto(Map<String,Object> b){
        String code=String.valueOf(b.getOrDefault("codigo","")), name=String.valueOf(b.getOrDefault("nombre","")), type=String.valueOf(b.getOrDefault("tipo","MEDICAMENTO")).toUpperCase(Locale.ROOT), batch=String.valueOf(b.getOrDefault("lote",""));
        if(!code.matches("[A-Za-z0-9]{8}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Código debe tener 8 caracteres alfanuméricos");
        if(name.isBlank()||name.length()>120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nombre obligatorio (máximo 120 caracteres)");
        if(!List.of("MEDICAMENTO","UTENSILIO").contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tipo permitido: MEDICAMENTO o UTENSILIO");
        if("MEDICAMENTO".equals(type)&&String.valueOf(b.getOrDefault("registroInvima","")).isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Registro Invima obligatorio");
        if(batch.isBlank()||batch.length()>80) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Número de lote obligatorio (máximo 80 caracteres)");
        try { if(new java.math.BigDecimal(String.valueOf(b.get("precioCompra"))).signum()<0||new java.math.BigDecimal(String.valueOf(b.get("precioVenta"))).signum()<0) throw new IllegalArgumentException(); java.math.BigDecimal iva=new java.math.BigDecimal(String.valueOf(b.getOrDefault("ivaPorcentaje",19))); if(iva.signum()<0||iva.compareTo(new java.math.BigDecimal("100"))>0)throw new IllegalArgumentException(); int qty=Integer.parseInt(String.valueOf(b.getOrDefault("cantidad",0))); int min=Integer.parseInt(String.valueOf(b.getOrDefault("stockMinimo",0))); if(qty<0||min<0) throw new IllegalArgumentException(); } catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Precios, IVA, cantidad y stock mínimo fuera de rango"); }
        try { LocalDate entry=b.get("fechaIngreso")==null?LocalDate.now():LocalDate.parse(String.valueOf(b.get("fechaIngreso"))); LocalDate expiry=LocalDate.parse(String.valueOf(b.get("fechaVencimiento"))); if(entry.isAfter(LocalDate.now())||expiry.isBefore(LocalDate.now())||expiry.isBefore(entry))throw new IllegalArgumentException(); } catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Fechas de ingreso o vencimiento inválidas"); }
    }
    private void crearProductoInterno(Map<String,Object> b,long usuario){Number med=findNumber("SELECT medicamento_id FROM medicamento WHERE LOWER(nombre)=LOWER(?)",b.get("nombre")); if(med==null){db.update("INSERT INTO medicamento(nombre,presentacion,descripcion) VALUES(?,?,?)",b.get("nombre"),b.get("presentacion"),b.get("descripcion"));med=db.queryForObject("SELECT LAST_INSERT_ID()",Number.class);}Object entry=b.getOrDefault("fechaIngreso",LocalDate.now());db.update("INSERT INTO medicamento_inventario(medicamento_id,codigo,tipo,registro_invima,precio_compra,precio_venta,stock_minimo,fecha_ingreso,iva_porcentaje) VALUES(?,?,?,?,?,?,?,?,?)",med,b.get("codigo"),b.get("tipo"),b.get("registroInvima"),b.get("precioCompra"),b.get("precioVenta"),b.get("stockMinimo"),entry,b.getOrDefault("ivaPorcentaje",19));Number inv=db.queryForObject("SELECT LAST_INSERT_ID()",Number.class);int quantity=((Number)b.getOrDefault("cantidad",0)).intValue();db.update("INSERT INTO lote_medicamento(inventario_id,numero_lote,fecha_ingreso,fecha_vencimiento,cantidad) VALUES(?,?,?,?,?)",inv,b.get("lote"),entry,b.get("fechaVencimiento"),quantity);Number lot=db.queryForObject("SELECT LAST_INSERT_ID()",Number.class);if(quantity>0)db.update("INSERT INTO movimiento_inventario(inventario_id,lote_id,tipo,cantidad,usuario_id,referencia) VALUES(?,?,'ENTRADA',?,?,?)",inv,lot,quantity,usuario,"IMPORTACION_MASIVA");}
    private List<String[]> parseRows(MultipartFile file) throws IOException {
        List<String[]> rows=new ArrayList<>(); String filename=String.valueOf(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if(filename.endsWith(".xlsx")){
            try(Workbook wb=WorkbookFactory.create(file.getInputStream())){
                DataFormatter formatter=new DataFormatter(Locale.ROOT); Sheet sheet=wb.getSheetAt(0);
                for(Row row:sheet){List<String> values=new ArrayList<>();for(int i=0;i<11;i++){Cell cell=row.getCell(i,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);String value="";if(cell!=null){if((i==8||i==9)&&cell.getCellType()==CellType.NUMERIC&&DateUtil.isCellDateFormatted(cell))value=cell.getDateCellValue().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();else value=formatter.formatCellValue(cell).trim();}values.add(value);}rows.add(values.toArray(String[]::new));}
            }
        }else{
            try(BufferedReader br=new BufferedReader(new InputStreamReader(file.getInputStream(),StandardCharsets.UTF_8))){String line;char delimiter=',';boolean first=true;while((line=br.readLine())!=null){if(first){if(line.startsWith("\uFEFF"))line=line.substring(1);if(count(line,';')>count(line,','))delimiter=';';first=false;}rows.add(parseDelimited(line,delimiter));}}
        }
        return rows;
    }
    private int count(String value,char target){int n=0;for(char c:value.toCharArray())if(c==target)n++;return n;}
    private String[] parseDelimited(String line,char delimiter){List<String> fields=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){field.append('"');i++;}else quoted=!quoted;}else if(c==delimiter&&!quoted){fields.add(field.toString().trim());field.setLength(0);}else field.append(c);}fields.add(field.toString().trim());return fields.toArray(String[]::new);}
}
