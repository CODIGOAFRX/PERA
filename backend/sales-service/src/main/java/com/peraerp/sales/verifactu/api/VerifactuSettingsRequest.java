package com.peraerp.sales.verifactu.api;

import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import com.peraerp.sales.verifactu.domain.VerifactuMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Lo que el usuario puede cambiar de la configuración de Veri*Factu.
 *
 * <p>Los datos del productor de software ({@code softwareName}, {@code softwareId},
 * {@code softwareVersion}, {@code developerTaxId}) <strong>no</strong> están aquí: identifican a
 * PERA como sistema informático de facturación, no a la empresa que lo usa, y vienen de la
 * configuración del despliegue.</p>
 */
public record VerifactuSettingsRequest(
        boolean enabled,
        VerifactuMode mode,
        VerifactuEnvironment environment,
        @NotBlank @Size(max = 20) String issuerTaxId,
        @NotBlank @Size(max = 180) String issuerLegalName,
        @Schema(description = "ClaveRegimen por defecto. 01 es el régimen general.")
        @Pattern(regexp = "\\d{2}") String defaultRegimeKey,
        @Schema(description = "CalificacionOperacion por defecto. S1 es operación sujeta y no exenta.")
        @Pattern(regexp = "[SN][1-9]") String defaultOperationQualification,
        @NotBlank @Size(max = 64) String timeZone) {
}
