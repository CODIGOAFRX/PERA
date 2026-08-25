package com.peraerp.masterdata.importing;

enum ImportKind {
    CUSTOMERS("clientes", new String[]{"codigo", "nombre_legal", "nombre_comercial", "identificador_fiscal",
            "tipo_identificacion_fiscal", "pais_fiscal", "telefono", "email", "observaciones",
            "limite_credito", "aviso_riesgo", "politica_riesgo", "activo"}),
    SUPPLIERS("proveedores", new String[]{"codigo", "nombre_legal", "nombre_comercial", "identificador_fiscal",
            "telefono", "email", "observaciones", "transportista", "ruta", "activo"}),
    PRODUCTS("articulos", new String[]{"codigo", "nombre", "descripcion", "unidad", "precio_base",
            "porcentaje_impuesto", "activo"});

    private final String fileStem;
    private final String[] headers;

    ImportKind(String fileStem, String[] headers) {
        this.fileStem = fileStem;
        this.headers = headers;
    }

    String fileStem() { return fileStem; }
    String[] headers() { return headers.clone(); }
}
