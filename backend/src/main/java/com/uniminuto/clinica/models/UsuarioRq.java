package com.uniminuto.clinica.models;

import lombok.Data;

@Data
public class UsuarioRq {
    private Long id;
    private String username;
    private String password;
    private String rol;
    private Boolean activo;
    private String email;
    private Long clienteId;
}
