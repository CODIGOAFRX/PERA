package com.peraerp.sales.verifactu.chain;

import com.peraerp.sales.verifactu.domain.VerifactuRecord;

import java.time.ZonedDateTime;

/**
 * Produce el XML de un registro una vez la cadena ya sabe con qué se encadena.
 *
 * <p>Existe por un orden que no se puede invertir: el XML contiene la huella, y la huella no se
 * puede calcular hasta tener el bloqueo y leer el registro anterior. Quien pide encadenar no
 * conoce todavía esos datos, así que en vez de un XML entrega la forma de construirlo.</p>
 *
 * <p>El efecto secundario es útil: la cadena sigue sin saber nada de XML ni de esquemas, y se
 * puede probar sin ellos.</p>
 */
@FunctionalInterface
public interface RecordPayloadFactory {

    String serialize(PayloadContext context);

    /**
     * Lo que la cadena aporta al serializador.
     *
     * @param previousFingerprint huella del registro anterior; {@code null} en el primero
     * @param fingerprint         huella ya calculada de este registro
     * @param generatedAt         marca de generación sellada dentro del bloqueo
     * @param previousRecord      registro anterior completo, para el bloque de encadenamiento
     */
    record PayloadContext(String previousFingerprint, String fingerprint, ZonedDateTime generatedAt,
                          VerifactuRecord previousRecord) {
    }
}
