package com.peraerp.sales.print;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Dibuja el QR de cotejo.
 *
 * <p>Nivel de corrección M y margen de 2 módulos, que es lo que fija la especificación del QR
 * tributario. No son adornos: un nivel distinto cambia los datos del código y un margen menor hace
 * que muchos lectores no lo encuentren sobre el papel.</p>
 *
 * <p>El contenido llega ya construido. Aquí no se compone la URL ni se ordenan los parámetros: eso
 * es especificación y vive en {@code VerifactuQrPayload}, un único sitio para no acabar con dos
 * versiones que se separan.</p>
 */
final class QrImages {

    private static final int MODULE_PIXELS = 8;

    private QrImages() {
    }

    static BufferedImage of(String payload) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        try {
            // El ancho pedido es orientativo: ZXing lo redondea al múltiplo de módulos que quepa.
            // Se pide holgado para que el resultado tenga bastantes píxeles por módulo y el QR no
            // salga con los bordes mordidos al escalarlo a 35 mm en el papel.
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE,
                    45 * MODULE_PIXELS, 45 * MODULE_PIXELS, hints);
            return toImage(matrix);
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el código QR de cotejo.", e);
        }
    }

    private static BufferedImage toImage(BitMatrix matrix) {
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < matrix.getWidth(); x++) {
            for (int y = 0; y < matrix.getHeight(); y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return image;
    }
}
