package com.peraerp.sales.integration;
import com.peraerp.sales.document.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.net.*;
import java.net.http.HttpClient;
import com.sun.net.httpserver.HttpServer;
import static org.assertj.core.api.Assertions.*;

class B2bTestTransportTest {
    CommercialDocument invoice() {
        var d=new CommercialDocument(UUID.randomUUID(),"FAC1",DocumentType.INVOICE,UUID.randomUUID(),"C1","Cliente",LocalDate.of(2026,9,23),null,"EUR",null,null,null);
        d.addLine(new DocumentLine(null,"P1","Producto",new BigDecimal("2"),new BigDecimal("27.95"),BigDecimal.ZERO,new BigDecimal("21")));
        org.springframework.test.util.ReflectionTestUtils.setField(d,"id",UUID.randomUUID());
        d.recalculate(new DocumentAmountsCalculator()); d.confirm(); return d;
    }
    @Test void preservesInvoiceTotalsAndCreatesOnlyADraft() {
        var d=invoice(); var payload=B2bTestTransport.payload(d,42);
        var json=JsonMapper.builder().build().valueToTree(payload);
        assertThat(json.path("send_after_import").asBoolean()).isFalse();
        var line=json.path("invoice").path("invoice_lines_attributes").get(0);
        assertThat(line.path("price").decimalValue()).isEqualByComparingTo("55.9");
        assertThat(line.path("quantity").decimalValue()).isEqualByComparingTo(line.path("base_quantity").decimalValue());
        assertThat(json.path("invoice").path("contact_id").asLong()).isEqualTo(42);
    }
    @Test void blocksRemoteRoundingDifferencesAndWrongCurrency() {
        var mapper=JsonMapper.builder().build(); var d=invoice();
        B2bTestTransport.verifyTotals(mapper.readTree("{\"total\":67.64,\"subtotal\":55.90,\"currency\":\"EUR\"}"),d);
        assertThatThrownBy(()->B2bTestTransport.verifyTotals(mapper.readTree("{\"total\":67.63,\"subtotal\":55.90,\"currency\":\"EUR\"}"),d)).hasMessageContaining("no coinciden");
        assertThatThrownBy(()->B2bTestTransport.verifyTotals(mapper.readTree("{\"total\":67.64,\"subtotal\":55.90,\"currency\":\"USD\"}"),d));
    }
    @Test void sendsSandboxHeadersAndRejectsProductionKeysBeforeNetwork() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var seen=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/accounts/42",exchange->{
            if("test_example".equals(exchange.getRequestHeaders().getFirst("X-B2B-API-Key")) && "2026-06-26".equals(exchange.getRequestHeaders().getFirst("X-B2B-API-Version"))) seen.incrementAndGet();
            byte[] bytes="{\"account\":{\"id\":42}}".getBytes();exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        }); server.start();
        try {
            var transport=new B2bTestTransport(JsonMapper.builder().build(),HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"));
            assertThat(transport.request("test_example","GET","accounts/42",null).path("account").path("id").asLong()).isEqualTo(42);
            assertThatThrownBy(()->transport.request("prod_example","GET","accounts/42",null)).hasMessageContaining("sandbox");
            assertThat(seen.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }
}
