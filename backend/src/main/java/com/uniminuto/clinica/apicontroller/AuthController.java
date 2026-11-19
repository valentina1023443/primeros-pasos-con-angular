package com.uniminuto.clinica.apicontroller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.uniminuto.clinica.util.PasswordUtil;
import io.swagger.v3.oas.annotations.Operation;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:4200", allowedHeaders = {"Authorization", "Content-Type"})
public class AuthController {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate db;

    public AuthController(JdbcTemplate db) {
        this.db = db;
    }

    @Operation(summary = "Iniciar sesión", description = "Autentica usuario y contraseña y devuelve un token de sesión temporal.", security = {})
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> request) {
        String username = request.getOrDefault("username", "").trim();
        String password = request.getOrDefault("password", "");
        if (username.isBlank() || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario y contraseña son obligatorios");
        }
        var users = db.queryForList("SELECT id,username,password_hash,rol,activo FROM usuario WHERE username=? LIMIT 1", username);
        if (users.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        Map<String, Object> user = users.get(0);
        if (!Boolean.TRUE.equals(user.get("activo")) && !"1".equals(String.valueOf(user.get("activo")))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario inactivo");
        }
        String stored = String.valueOf(user.get("password_hash"));
        if (!PasswordUtil.matches(password, stored)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }
        if (!stored.startsWith("PBKDF2$")) db.update("UPDATE usuario SET password_hash=? WHERE id=?", PasswordUtil.hash(password), user.get("id"));
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime expires = LocalDateTime.now().plusHours(8);
        long userId = ((Number) user.get("id")).longValue();
        db.update("INSERT INTO session(user_id,token,fecha_ini_sesion,fecha_expiracion) VALUES(?,?,NOW(),?)", userId, token, expires);
        String rawRole = String.valueOf(user.get("rol")).trim().toUpperCase();
        String role = switch (rawRole.replace(' ', '_')) {
            case "ADMIN", "ADMINISTRADOR" -> "ADMINISTRADOR";
            case "RECEPCION", "SECRETARIA" -> "SECRETARIA";
            case "MEDICO", "VETERINARIO" -> "MEDICO";
            case "DESPACHO_MEDICO" -> "DESPACHO_MEDICO";
            default -> rawRole;
        };
        return Map.of("accessToken", token, "tokenType", "Bearer", "expiresAt", expires.toString(),
                "userId", userId, "username", String.valueOf(user.get("username")), "role", role);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
        if (!token.isBlank()) db.update("DELETE FROM session WHERE token=?", token);
        return Map.of("message", "Sesión cerrada");
    }
}
