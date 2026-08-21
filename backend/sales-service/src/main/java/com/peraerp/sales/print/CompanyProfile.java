package com.peraerp.sales.print;

/**
 * Datos de la empresa que se imprimen en la cabecera de la factura.
 *
 * <p>Se toman de identity-service en el momento de imprimir, no de un snapshot de la factura. Es
 * deliberado y tiene su matiz: si la empresa se muda, una reimpresión de una factura antigua
 * llevará el domicilio nuevo. Para el emisor eso es lo correcto —es la misma empresa y el
 * domicilio vigente es el que sirve para localizarla—, mientras que el del destinatario sí habrá
 * que congelarlo, porque puede haber dejado de ser cliente.</p>
 */
public record CompanyProfile(
        String displayName,
        String addressLine1,
        String addressLine2,
        String postalCode,
        String city,
        String region,
        String phone,
        String contactEmail,
        String invoiceEmail,
        String website) {

    /** El correo de facturación manda sobre el de contacto: es el que atiende dudas de la factura. */
    public String preferredEmail() {
        return invoiceEmail == null || invoiceEmail.isBlank() ? contactEmail : invoiceEmail;
    }
}
