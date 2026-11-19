package com.uniminuto.clinica.apicontroller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Protege los módulos nuevos con tokens opacos guardados en la tabla session.
 * Los valores X-Role/X-User-Id enviados por el navegador nunca se consideran confiables.
 */
@Component
public class BearerSessionFilter extends OncePerRequestFilter {
    public static final String USER_ID = "authenticatedUserId";
    public static final String USER_ROLE = "authenticatedUserRole";
    private final JdbcTemplate db;

    public BearerSessionFilter(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/auth/") || path.contains("/swagger-ui") || path.contains("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) { chain.doFilter(request, response); return; }
        String path = request.getRequestURI();
        if (!isProtectedPath(path)) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                ? header.substring(7).trim() : "";
        if (token.isBlank()) {
            reject(response, "Se requiere una sesión Bearer válida");
            return;
        }
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT u.id, UPPER(REPLACE(REPLACE(COALESCE(NULLIF(u.rol,''),p.nombre),' ', '_'),'-','_')) rol " +
                "FROM session s JOIN usuario u ON u.id=s.user_id LEFT JOIN perfil p ON p.perfil_id=u.perfil_id " +
                "WHERE s.token=? AND u.activo=1 AND (s.fecha_expiracion IS NULL OR s.fecha_expiracion>NOW()) " +
                "ORDER BY s.session_id DESC LIMIT 1", token);
        if (rows.isEmpty()) {
            reject(response, "La sesión expiró o no es válida");
            return;
        }
        Map<String, Object> user = rows.get(0);
        long userId = ((Number) user.get("id")).longValue();
        String role = normalizeRole(String.valueOf(user.get("rol")));
        if (!authorized(path, request.getMethod(), role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"El perfil autenticado no tiene acceso a este recurso\"}");
            return;
        }
        request.setAttribute(USER_ID, userId);
        request.setAttribute(USER_ROLE, role);
        HttpServletRequestWrapper trusted = new HttpServletRequestWrapper(request) {
            @Override public String getHeader(String name) {
                if ("X-Role".equalsIgnoreCase(name)) return role;
                if ("X-User-Id".equalsIgnoreCase(name)) return Long.toString(userId);
                return super.getHeader(name);
            }
            @Override public Enumeration<String> getHeaders(String name) {
                String trustedValue = getHeader(name);
                return trustedValue == null ? super.getHeaders(name) : Collections.enumeration(List.of(trustedValue));
            }
            @Override public Enumeration<String> getHeaderNames() {
                Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
                names.add("X-Role");
                names.add("X-User-Id");
                return Collections.enumeration(names);
            }
        };
        chain.doFilter(trusted, response);
    }

    private boolean isProtectedPath(String path) {
        return List.of("/operaciones/", "/gestion/", "/cita/", "/medicamento/", "/formula-medica/", "/historia-medica/", "/usuario/", "/reportes/",
                        "/cliente/", "/mascota/", "/raza/", "/medico/", "/especializacion/", "/portal-cliente/")
                .stream().anyMatch(path::contains);
    }

    private boolean authorized(String path, String method, String role) {
        boolean write = !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);
        if (path.contains("/portal-cliente/")) return "CLIENTE".equals(role);
        if (path.contains("/cliente/")) return List.of("ADMINISTRADOR", "SECRETARIA").contains(role);
        if (path.contains("/mascota/")) return List.of("ADMINISTRADOR", "SECRETARIA", "MEDICO", "DESPACHO_MEDICO").contains(role);
        if (path.contains("/raza/")) return write ? "ADMINISTRADOR".equals(role) : List.of("ADMINISTRADOR", "SECRETARIA", "MEDICO", "DESPACHO_MEDICO").contains(role);
        if (path.contains("/medico/")) return write ? List.of("ADMINISTRADOR", "SECRETARIA").contains(role) : List.of("ADMINISTRADOR", "SECRETARIA", "MEDICO").contains(role);
        if (path.contains("/especializacion/")) return write ? "ADMINISTRADOR".equals(role) : List.of("ADMINISTRADOR", "SECRETARIA", "MEDICO").contains(role);
        if (path.contains("/operaciones/inventario/importar") || (path.contains("/operaciones/inventario") && write)) return "ADMINISTRADOR".equals(role);
        if (path.contains("/operaciones/inventario")) return List.of("ADMINISTRADOR","DESPACHO_MEDICO","MEDICO").contains(role);
        if (path.contains("/operaciones/despachos")) return List.of("ADMINISTRADOR","DESPACHO_MEDICO").contains(role);
        if (path.contains("/operaciones/facturas")) return List.of("ADMINISTRADOR","SECRETARIA").contains(role);
        if (path.contains("/operaciones/reportes")) return List.of("ADMINISTRADOR","SECRETARIA").contains(role);
        if (path.contains("/gestion/reportes")) return List.of("ADMINISTRADOR","SECRETARIA").contains(role);
        if (path.contains("/gestion/tarifas")) return write ? "ADMINISTRADOR".equals(role) : List.of("ADMINISTRADOR","SECRETARIA").contains(role);
        if (path.contains("/gestion/prefacturas")) return List.of("ADMINISTRADOR","SECRETARIA").contains(role);
        if (path.contains("/gestion/atenciones") || path.contains("/gestion/formulas")) return List.of("ADMINISTRADOR","MEDICO").contains(role);
        if (path.contains("/gestion/documentos")) return List.of("ADMINISTRADOR","SECRETARIA","MEDICO","DESPACHO_MEDICO").contains(role);
        if (path.contains("/gestion/citas") || path.contains("/gestion/horarios")) return List.of("ADMINISTRADOR","SECRETARIA","MEDICO").contains(role);
        if (path.contains("/formula-medica/") || path.contains("/historia-medica/")) return List.of("ADMINISTRADOR","MEDICO").contains(role);
        if (path.contains("/cita/")) return List.of("ADMINISTRADOR","SECRETARIA","MEDICO").contains(role);
        if (path.contains("/reportes/")) return List.of("ADMINISTRADOR","SECRETARIA").contains(role);
        if (path.contains("/medicamento/")) return write ? "ADMINISTRADOR".equals(role) : List.of("ADMINISTRADOR","MEDICO","DESPACHO_MEDICO").contains(role);
        if (path.contains("/usuario/")) return "ADMINISTRADOR".equals(role);
        return false;
    }

    private String normalizeRole(String role) {
        String normalized = role.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (normalized) {
            case "ADMIN", "ADMINISTRADOR" -> "ADMINISTRADOR";
            case "RECEPCION", "RECEPCIÓN", "SECRETARIA", "SECRETARÍA" -> "SECRETARIA";
            case "MEDICO", "MÉDICO", "VETERINARIO" -> "MEDICO";
            case "DESPACHO_MEDICO", "DESPACHO_MÉDICO" -> "DESPACHO_MEDICO";
            default -> normalized;
        };
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}");
    }
}
