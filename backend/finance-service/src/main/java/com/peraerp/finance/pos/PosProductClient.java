package com.peraerp.finance.pos;

import java.math.BigDecimal;
import java.util.UUID;

/** Consulta el artículo en master-data: el TPV no se fía del nombre, precio ni impuesto que mande el navegador. */
public interface PosProductClient {

    ProductSnapshot findProduct(UUID productId);

    record ProductSnapshot(UUID id, String code, String name, String barcode, String unitOfMeasure,
                           BigDecimal basePrice, BigDecimal taxRate, boolean active) {
    }
}
