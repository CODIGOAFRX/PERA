package com.peraerp.sales.integration;
import com.peraerp.sales.verifactu.xml.*;
import com.peraerp.sales.verifactu.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AeatSchemaTest {
    @Test void actualGeneratedRecordValidatesAgainstOfficialOfflineSchema() throws Exception {
        var content=new RegistroAltaContent("89890001K","Empresa de pruebas","FAC1",LocalDate.of(2026,9,23),InvoiceKind.F1,null,List.of(),"Venta",
                new RegistroAltaContent.Recipient("Cliente","89890002E",TaxIdentificationType.NIF,"ES"),
                List.of(new RegistroAltaContent.BreakdownDetail("01",OperationQualification.SUBJECT_NOT_EXEMPT,null,new BigDecimal("21"),new BigDecimal("55.90"),new BigDecimal("11.74"))),
                new BigDecimal("11.74"),new BigDecimal("67.64"),null,
                new RegistroAltaContent.SoftwareSystem("Productor","89890001K","PERA","01","0.1.0","TEST-INSTALL",false),
                ZonedDateTime.parse("2026-09-23T10:00:00+02:00"),"A".repeat(64));
        String envelope=AeatTestTransport.envelope(new RegistroAltaXmlWriter().write(content));
        AeatSchema.validate(envelope);
        assertThatThrownBy(()->AeatSchema.validate(envelope.replace("<sf:CuotaTotal>11.74</sf:CuotaTotal>",""))).hasMessageContaining("CuotaTotal");
    }
}
