package com.peraerp.sales.mail;

import java.net.InetAddress;

/** Nombre del equipo para el saludo SMTP (EHLO), leído una vez y sin consultar el DNS inverso. */
final class LocalHostName {
    static final String VALUE = resolve();

    private LocalHostName() {}

    private static String resolve() {
        try {
            String name = InetAddress.getLocalHost().getHostName();
            return name == null || name.isBlank() ? "localhost" : name;
        } catch (Exception e) {
            return "localhost";
        }
    }
}
