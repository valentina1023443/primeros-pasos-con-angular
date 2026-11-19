package com.uniminuto.clinica.apicontroller;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/gestion/reportes")
@CrossOrigin(origins = "http://localhost:4200")
public class ReportesClinicaController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final JdbcTemplate db;

    public ReportesClinicaController(JdbcTemplate db) { this.db = db; }

    @GetMapping("/{tipo}/preview")
    public Map<String,Object> preview(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,
            @PathVariable String tipo, @RequestParam String desde, @RequestParam String hasta,
            @RequestParam(required = false) Long medicoId, @RequestParam(required = false) String estado) {
        validarPermiso(role);
        validarFechas(desde, hasta);
        List<Map<String,Object>> rows = rows(tipo, desde, hasta, medicoId, estado);
        Map<String,Object> totals = totals(rows);
        return Map.of("tipo", tipo, "desde", desde, "hasta", hasta,
                "generadoEn", LocalDateTime.now().toString(), "cantidad", rows.size(),
                "columnas", rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet()),
                "filas", rows, "totales", totals);
    }

    @GetMapping("/{tipo}/descargar")
    public ResponseEntity<byte[]> descargar(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestHeader("X-Role") String role,
            @PathVariable String tipo, @RequestParam String desde, @RequestParam String hasta,
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long medicoId, @RequestParam(required = false) String estado) throws Exception {
        validarPermiso(role);
        validarFechas(desde, hasta);
        if (!"xlsx".equalsIgnoreCase(formato) && !"pdf".equalsIgnoreCase(formato)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato permitido: xlsx o pdf");
        }
        List<Map<String,Object>> rows = rows(tipo, desde, hasta, medicoId, estado);
        byte[] bytes = "pdf".equalsIgnoreCase(formato) ? pdf(tipo, desde, hasta, rows) : excel(tipo, desde, hasta, rows);
        String extension = formato.toLowerCase(Locale.ROOT);
        MediaType mediaType = extension.equals("pdf") ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(XLSX);
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-" + safeName(tipo) + "." + extension)
                .body(bytes);
    }

    private void validarPermiso(String role) {
        if (!"ADMINISTRADOR".equalsIgnoreCase(role) && !"SECRETARIA".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Perfil no autorizado para reportes administrativos");
        }
    }

    private void validarFechas(String desde, String hasta) {
        try {
            LocalDate start = LocalDate.parse(desde);
            LocalDate end = LocalDate.parse(hasta);
            if (end.isBefore(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha final debe ser igual o posterior a la inicial");
            if (end.isAfter(LocalDate.now().plusYears(5))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rango de fechas no válido");
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use fechas yyyy-MM-dd");
        }
    }

    private List<Map<String,Object>> rows(String tipo, String desde, String hasta, Long medicoId, String estado) {
        String normalized = tipo.toLowerCase(Locale.ROOT);
        String from = desde + " 00:00:00";
        String to = hasta + " 23:59:59";
        if ("inventario".equals(normalized)) {
            return db.queryForList("SELECT m.nombre medicamento,i.codigo,i.tipo,i.registro_invima,i.precio_compra costo_unitario,i.precio_venta venta_unitaria,i.stock_minimo,COALESCE(SUM(l.cantidad),0) existencias,SUM(CASE WHEN l.fecha_vencimiento>=CURDATE() THEN l.cantidad ELSE 0 END) stock_vigente,SUM(CASE WHEN l.fecha_vencimiento<CURDATE() THEN l.cantidad ELSE 0 END) vencido,MIN(CASE WHEN l.fecha_vencimiento>=CURDATE() AND l.cantidad>0 THEN l.fecha_vencimiento END) proximo_vencimiento,SUM(l.cantidad*i.precio_compra) valor_costo,SUM(l.cantidad*i.precio_venta) valor_potencial,CASE WHEN SUM(CASE WHEN l.fecha_vencimiento>=CURDATE() THEN l.cantidad ELSE 0 END)=0 THEN 'AGOTADO' WHEN SUM(CASE WHEN l.fecha_vencimiento>=CURDATE() THEN l.cantidad ELSE 0 END)<=i.stock_minimo THEN 'STOCK_BAJO' ELSE 'DISPONIBLE' END estado FROM medicamento_inventario i JOIN medicamento m ON m.medicamento_id=i.medicamento_id LEFT JOIN lote_medicamento l ON l.inventario_id=i.id WHERE i.activo=1 GROUP BY i.id,m.nombre,i.codigo,i.tipo,i.registro_invima,i.precio_compra,i.precio_venta,i.stock_minimo ORDER BY m.nombre");
        }
        if ("movimientos".equals(normalized) || "inventario-movimientos".equals(normalized)) {
            return db.queryForList("SELECT x.fecha,x.tipo,m.nombre medicamento,i.codigo,l.numero_lote,x.cantidad,x.referencia,x.formula_id,x.despacho_id,u.username usuario FROM movimiento_inventario x JOIN medicamento_inventario i ON i.id=x.inventario_id JOIN medicamento m ON m.medicamento_id=i.medicamento_id JOIN lote_medicamento l ON l.id=x.lote_id JOIN usuario u ON u.id=x.usuario_id WHERE x.fecha BETWEEN ? AND ? ORDER BY x.fecha DESC", from, to);
        }
        if ("ventas-medicamentos".equals(normalized)) {
            String sql="SELECT f.fecha,f.id factura_id,CONCAT(cl.nombres,' ',cl.apellidos) propietario,ma.nombre_mascota mascota,fd.concepto medicamento,fd.cantidad,fd.precio_unitario,fd.iva_porcentaje,(fd.cantidad*fd.precio_unitario) subtotal FROM factura_detalle fd JOIN factura f ON f.id=fd.factura_id JOIN cliente cl ON cl.cliente_id=f.cliente_id JOIN mascota ma ON ma.mascota_id=f.mascota_id WHERE f.fecha BETWEEN ? AND ? AND f.estado<>'ANULADA' AND fd.concepto LIKE 'Medicamento dispensado:%'";
            List<Object> args=new ArrayList<>(List.of(from,to));if(medicoId!=null){sql+=" AND EXISTS(SELECT 1 FROM citas c WHERE c.id=f.cita_id AND c.medico_id=?)";args.add(medicoId);}sql+=" ORDER BY f.fecha DESC";return db.queryForList(sql,args.toArray());
        }
        if ("ingresos-especialidad".equals(normalized) || "ingresos-veterinario".equals(normalized)) {
            String dimension="ingresos-especialidad".equals(normalized)?"e.nombre":"CONCAT(md.nombres,' ',md.apellidos)";
            String sql="SELECT "+dimension+" agrupacion,COUNT(DISTINCT f.id) facturas,SUM(f.total) total_facturado,SUM(COALESCE(p.pagado,0)) ingresos_cobrados,SUM(f.iva) impuestos,SUM(f.descuento) descuentos FROM factura f JOIN citas c ON c.id=f.cita_id JOIN medico md ON md.id=c.medico_id LEFT JOIN especializacion e ON e.id=md.especializacion_id LEFT JOIN (SELECT factura_id,SUM(valor) pagado FROM pago_factura GROUP BY factura_id) p ON p.factura_id=f.id WHERE f.fecha BETWEEN ? AND ? AND f.estado<>'ANULADA'";
            List<Object> args=new ArrayList<>(List.of(from,to));if(medicoId!=null){sql+=" AND c.medico_id=?";args.add(medicoId);}if(estado!=null&&!estado.isBlank()){sql+=" AND f.estado=?";args.add(estado.toUpperCase(Locale.ROOT));}sql+=" GROUP BY agrupacion ORDER BY agrupacion";return db.queryForList(sql,args.toArray());
        }
        if (List.of("ingresos-dia","ingresos-mes","ingresos-anio").contains(normalized)) {
            String period=switch(normalized){case "ingresos-dia"->"DATE(p.pagado_en)";case "ingresos-mes"->"DATE_FORMAT(p.pagado_en,'%Y-%m')";default->"YEAR(p.pagado_en)";};
            String sql="SELECT "+period+" periodo,COUNT(DISTINCT f.id) facturas,COUNT(p.id) pagos,SUM(p.valor) ingresos_cobrados FROM pago_factura p JOIN factura f ON f.id=p.factura_id WHERE p.pagado_en BETWEEN ? AND ? AND f.estado<>'ANULADA' GROUP BY periodo ORDER BY periodo";
            return db.queryForList(sql,from,to);
        }
        if ("facturas".equals(normalized) || "financiero".equals(normalized)) {
            String sql = "SELECT f.id factura_id,f.fecha,CONCAT(cl.nombres,' ',cl.apellidos) propietario,ma.nombre_mascota mascota,CONCAT(md.nombres,' ',md.apellidos) veterinario,e.nombre especialidad,f.subtotal,f.iva,f.descuento,f.total,f.estado,COALESCE(p.pagado,0) pagado,(f.total-COALESCE(p.pagado,0)) saldo FROM factura f JOIN cliente cl ON cl.cliente_id=f.cliente_id JOIN mascota ma ON ma.mascota_id=f.mascota_id JOIN citas c ON c.id=f.cita_id JOIN medico md ON md.id=c.medico_id LEFT JOIN especializacion e ON e.id=md.especializacion_id LEFT JOIN (SELECT factura_id,SUM(valor) pagado FROM pago_factura GROUP BY factura_id) p ON p.factura_id=f.id WHERE f.fecha BETWEEN ? AND ?";
            List<Object> args = new ArrayList<>(List.of(from,to));
            if (estado != null && !estado.isBlank()) { sql += " AND f.estado=?"; args.add(estado.toUpperCase(Locale.ROOT)); }
            if (medicoId != null) { sql += " AND c.medico_id=?"; args.add(medicoId); }
            sql += " ORDER BY f.fecha DESC";
            return db.queryForList(sql,args.toArray());
        }
        if ("citas-medico".equals(normalized) || "citas".equals(normalized) || "medico".equals(normalized)) {
            String sql = "SELECT c.id cita_id,c.fecha,c.fecha_fin,c.estado,c.motivo,CONCAT(md.nombres,' ',md.apellidos) veterinario,e.nombre especialidad,ma.nombre_mascota mascota,CONCAT(cl.nombres,' ',cl.apellidos) propietario FROM citas c JOIN medico md ON md.id=c.medico_id JOIN especializacion e ON e.id=md.especializacion_id JOIN mascota ma ON ma.mascota_id=c.mascota_id JOIN cliente cl ON cl.cliente_id=ma.cliente_id WHERE c.fecha BETWEEN ? AND ?";
            List<Object> args = new ArrayList<>(List.of(from,to));
            if (medicoId != null) { sql += " AND c.medico_id=?"; args.add(medicoId); }
            if (estado != null && !estado.isBlank()) { sql += " AND c.estado=?"; args.add(estado.toUpperCase(Locale.ROOT)); }
            if ("medico".equals(normalized)) sql = "SELECT c.estado,COUNT(*) citas,COUNT(DISTINCT c.mascota_id) mascotas,md.id medico_id,CONCAT(md.nombres,' ',md.apellidos) veterinario,e.nombre especialidad FROM citas c JOIN medico md ON md.id=c.medico_id JOIN especializacion e ON e.id=md.especializacion_id WHERE c.fecha BETWEEN ? AND ?" + (medicoId == null ? "" : " AND c.medico_id=?") + (estado == null || estado.isBlank() ? "" : " AND c.estado=?") + " GROUP BY c.estado,md.id,md.nombres,md.apellidos,e.nombre ORDER BY c.estado, veterinario";
            else sql += " ORDER BY c.fecha DESC";
            return db.queryForList(sql,args.toArray());
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tipo de reporte no soportado. Use financiero, facturas, ventas-medicamentos, ingresos-especialidad, ingresos-veterinario, ingresos-dia/mes/anio, inventario, movimientos, citas o medico");
    }

    private Map<String,Object> totals(List<Map<String,Object>> rows) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("filas", rows.size());
        if (rows.isEmpty()) return result;
        Set<String> numeric = new LinkedHashSet<>();
        for (String key : rows.get(0).keySet()) {
            String k = key.toLowerCase(Locale.ROOT);
            if (k.matches(".*(total|subtotal|iva|impuesto|descuento|pagado|saldo|existencias|vigente|vencido|costo|potencial|citas|mascotas|cantidad).*")) numeric.add(key);
        }
        for (String key : numeric) {
            BigDecimal sum = BigDecimal.ZERO;
            boolean found = false;
            for (Map<String,Object> row : rows) {
                Object value = row.get(key);
                if (value instanceof Number n) { sum = sum.add(new BigDecimal(n.toString())); found = true; }
            }
            if (found) result.put(key, sum);
        }
        return result;
    }

    private byte[] excel(String tipo, String desde, String hasta, List<Map<String,Object>> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Reporte");
            CellStyle title = workbook.createCellStyle();
            Font font = workbook.createFont(); font.setBold(true); title.setFont(font);
            Row first = sheet.createRow(0); first.createCell(0).setCellValue("Clínica Veterinaria Uniminuto — Reporte " + tipo); first.getCell(0).setCellStyle(title);
            Row meta = sheet.createRow(1); meta.createCell(0).setCellValue("Período: " + desde + " a " + hasta + " | Generado: " + LocalDateTime.now());
            List<String> columns = rows.isEmpty() ? List.of("resultado") : new ArrayList<>(rows.get(0).keySet());
            Row header = sheet.createRow(3);
            for (int c=0;c<columns.size();c++) { Cell cell=header.createCell(c); cell.setCellValue(columns.get(c)); cell.setCellStyle(title); }
            int r=4;
            for (Map<String,Object> data : rows) {
                Row row=sheet.createRow(r++); int c=0;
                for (String column : columns) setCell(row.createCell(c++), data.get(column));
            }
            Map<String,Object> totals=totals(rows);
            Row totalRow=sheet.createRow(r+1); totalRow.createCell(0).setCellValue("Totales");
            int c=1; for (Map.Entry<String,Object> entry : totals.entrySet()) { totalRow.createCell(c++).setCellValue(entry.getKey()+": "+String.valueOf(entry.getValue())); }
            for (int i=0;i<columns.size();i++) sheet.autoSizeColumn(i);
            ByteArrayOutputStream out=new ByteArrayOutputStream(); workbook.write(out); return out.toByteArray();
        }
    }

    private void setCell(Cell cell, Object value) {
        if (value == null) cell.setBlank();
        else if (value instanceof Number n) cell.setCellValue(n.doubleValue());
        else if (value instanceof Boolean b) cell.setCellValue(b);
        else cell.setCellValue(String.valueOf(value));
    }

    private byte[] pdf(String tipo, String desde, String hasta, List<Map<String,Object>> rows) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        Document document=new Document(); PdfWriter.getInstance(document,out); document.open();
        document.add(new Paragraph("Clínica Veterinaria Uniminuto"));
        document.add(new Paragraph("Reporte: "+tipo+" | Período: "+desde+" a "+hasta));
        document.add(new Paragraph("Generado: "+LocalDateTime.now()+" | Registros: "+rows.size()));
        if (!rows.isEmpty()) {
            List<String> columns=new ArrayList<>(rows.get(0).keySet());
            PdfPTable table=new PdfPTable(Math.max(1,columns.size())); table.setWidthPercentage(100);
            for(String column:columns) table.addCell(column);
            for(Map<String,Object> row:rows) for(String column:columns) table.addCell(String.valueOf(row.getOrDefault(column,"")));
            document.add(table);
        } else document.add(new Paragraph("Sin registros para el período seleccionado."));
        document.add(new Paragraph("Totales: "+totals(rows)));
        document.close(); return out.toByteArray();
    }

    private String safeName(String input) { return input.replaceAll("[^A-Za-z0-9_-]", "-"); }
}
