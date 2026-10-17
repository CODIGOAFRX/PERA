package com.peraerp.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo pública (la del portfolio): una empresa con datos inventados en la que se entra sin
 * contraseña como visitante.
 *
 * <p>Apagada por defecto. Solo se activa en el despliegue de la demo, con {@code PERA_DEMO_ENABLED}.
 * La empresa es distinta de la DEMO de desarrollo para que lo que hagan los visitantes no se
 * mezcle con las pruebas del equipo.</p>
 */
@ConfigurationProperties("pera.demo")
public record DemoProperties(boolean enabled, String companyCode, String companyName, String companyTaxId,
                             String username, String displayName) {
}
