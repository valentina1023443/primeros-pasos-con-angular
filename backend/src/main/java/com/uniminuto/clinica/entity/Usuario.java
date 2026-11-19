package com.uniminuto.clinica.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
@Data
public class Usuario {

    /** Identificador único del usuario. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Nombre de usuario. */
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /** Contraseña almacenada como hash PBKDF2. */
    @Column(name = "password_hash", columnDefinition = "TEXT")
    private String passwordHash;

    /** Rol textual consumido por las APIs existentes. */
    @Column(name = "rol", nullable = false, length = 30)
    private String rol;

    @Column(name = "perfil_id")
    private Integer perfilId;

    /** Ficha de propietario asociada exclusivamente a cuentas con rol CLIENTE. */
    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "activo")
    private Boolean activo;

    @Column(name = "email", nullable = false, length = 255)
    private String email;
}
