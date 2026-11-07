package com.peraerp.sales.numbering;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Comportamiento de una serie de documentos, como las opciones de la ficha de series de DimproCristalWin
 * ({@code series_facturas}, tabla {@code fseries}). Entre paréntesis, el campo de Dimpro del que sale cada una.
 */
public record SeriesOptions(
        @Schema(description = "Orden en las listas (orden).") @Min(0) @Max(9999) int displayOrder,
        @Schema(description = "Color de la serie en las listas, #RRGGBB (color_grid).") @Pattern(regexp = "#[0-9A-Fa-f]{6}") String color,
        @Schema(description = "Agrupación para listados (id_agrupa).") @Size(max = 60) String listingGroup,
        @Schema(description = "Serie a la que pasa al convertir (id_pasar_a).") UUID nextSchemeId,
        @Schema(description = "El documento nuevo lleva el número del anterior (numdocu_igual).") boolean sameNumberAsSource,
        @Schema(description = "El documento nuevo lleva la fecha del anterior (ffra_igual_falb).") boolean sameDateAsSource,
        @Schema(description = "Pide el número al crear, sin tocar el contador (pide_numdocu).") boolean asksNumber,
        @Schema(description = "Pasa las notas al documento al que se convierte (pasa_nota_docu).") boolean carriesNotes,
        @Schema(description = "Texto al pie del documento (texto_documento).") @Size(max = 200) String footerText,
        @Schema(description = "Copias además del original al imprimir (copias_doc).") @Min(0) @Max(9) int printCopies,
        @Schema(description = "Se imprime con precios e importes (documento_valorado).") boolean priced,
        @Schema(description = "Documento interno: no se envía al cliente (interno).") boolean internal,
        @Schema(description = "Reposición: todos los importes a cero (es_reposicion).") boolean zeroAmounts,
        @Schema(description = "Cuenta en el riesgo del cliente (es_riesgo).") boolean countsForRisk,
        @Schema(description = "Avisa si pasados estos días no se ha pasado a otro documento; 0 sin aviso (dias_aviso).") @Min(0) @Max(999) int remindAfterDays
) {
    /** Lo que tenía una numeración antes de que existieran estas opciones: el comportamiento de siempre. */
    public static SeriesOptions defaults() {
        return new SeriesOptions(0, null, null, null, false, false, false, true, null, 0, true, false, false, true, 0);
    }
}
