package com.uniminuto.clinica.models;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UsuarioRs {
    private Long id;
    private String username;
    private String rol;
    private LocalDateTime fechaCreacion;
    private Boolean activo;
    private String email;
    private Long clienteId;
}
