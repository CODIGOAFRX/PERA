package com.peraerp.sales.print;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Todo lo que se imprime en una factura, ya reunido y sin nada que consultar.
 *
 * <p>El renderizador no habla con la base de datos ni con otros servicios: recibe esto y dibuja.
 * Así la maqueta se puede probar con datos inventados, que es la única forma razonable de probar un
 * PDF.</p>
 *
 * @param verifactu bloque de cotejo, o {@code null} si la empresa no tiene Veri*Factu activado. La
 *                  leyenda y el QR solo corresponden a las facturas que han generado registro
 * @param logo      PNG o JPEG del logotipo, o {@code null}
 */
public record InvoicePdfContent(
        Issuer issuer,
        Recipient recipient,
        String title,
        String number,
        LocalDate issueDate,
        LocalDate dueDate,
        String invoiceKind,
        String rectifiedNumber,
        LocalDate rectifiedIssueDate,
        String currency,
        List<Line> lines,
        List<TaxRow> taxes,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String paymentMethod,
        String notes,
        Verifactu verifactu,
        byte[] logo) {

    /** Obligado a expedir. Sale de los ajustes de empresa y de la configuración Veri*Factu. */
    public record Issuer(String legalName, String taxId, String addressLine1, String addressLine2,
                         String postalCode, String city, String region, String phone, String email,
                         String website) {
    }

    /**
     * Destinatario.
     *
     * @param addressNotice aviso que ocupa el sitio del domicilio mientras PERA no lo guarde. El
     *                      art. 6.1.e) del RD 1619/2012 lo exige, así que el hueco se marca en vez
     *                      de disimularlo
     */
    public record Recipient(String legalName, String taxId, String code, String addressNotice) {
    }

    public record Line(int order, String code, String description, BigDecimal quantity,
                       BigDecimal unitPrice, BigDecimal discountPercentage, BigDecimal taxPercentage,
                       BigDecimal netAmount) {
    }

    /** Una fila del desglose por tipo de IVA. */
    public record TaxRow(BigDecimal rate, BigDecimal base, BigDecimal amount) {
    }

    public record Verifactu(String qrPayload, String fingerprint, String validationUrl) {
    }
}
