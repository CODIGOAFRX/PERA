package com.peraerp.sales.integration;

import javax.xml.XMLConstants;
import javax.xml.validation.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import org.w3c.dom.ls.DOMImplementationLS;
import java.util.Set;

/** Pinned official AEAT schemas, resolved exclusively from the application resources. */
final class AeatSchema {
    private static final Schema SCHEMA=load();
    private static Schema load() {
        try {
            var factory=SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            factory.setResourceResolver((type,namespace,publicId,systemId,base)->{
                if(!Set.of("SuministroInformacion.xsd","xmldsig-core-schema.xsd").contains(systemId)) throw new IllegalArgumentException("Esquema externo no permitido");
                try {
                    var impl=(DOMImplementationLS)DocumentBuilderFactory.newInstance().newDocumentBuilder().getDOMImplementation().getFeature("LS","3.0");
                    var input=impl.createLSInput(); input.setSystemId(systemId);
                    input.setByteStream(AeatSchema.class.getResourceAsStream("/verifactu/xsd/"+systemId)); return input;
                } catch(Exception e) { throw new IllegalStateException(e); }
            });
            return factory.newSchema(new StreamSource(AeatSchema.class.getResourceAsStream("/verifactu/xsd/SuministroLR.xsd")));
        } catch(Exception e) { throw new IllegalStateException("No se pudieron cargar los esquemas de la AEAT",e); }
    }
    static void validate(String envelope) throws Exception {
        var doc=AeatTestTransport.xml(envelope);
        var payload=doc.getElementsByTagNameNS(AeatTestTransport.LR,"RegFactuSistemaFacturacion").item(0);
        SCHEMA.newValidator().validate(new DOMSource(payload));
    }
}
