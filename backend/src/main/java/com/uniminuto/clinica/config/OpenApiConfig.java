package com.uniminuto.clinica.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "API Clínica Veterinaria Uniminuto",
        version = "1.0.0",
        description = "API de inventario, agenda, atención, fórmulas, despacho, facturación y reportes. Los permisos se derivan de la sesión autenticada; no confíe en roles enviados por el cliente."
    ),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "Token opaco de sesión"
)
public class OpenApiConfig {
}
