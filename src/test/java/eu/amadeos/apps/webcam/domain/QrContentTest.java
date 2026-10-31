package eu.amadeos.apps.webcam.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QrContentTest {
    @Test
    void kinds() {
        assertEquals(QrContent.Kind.LINK, QrContent.of(" https://amadeos.eu ").kind());
        assertEquals("https://amadeos.eu", QrContent.of(" https://amadeos.eu ").text());
        assertEquals(QrContent.Kind.LINK, QrContent.of("HTTP://x").kind());
        assertEquals(QrContent.Kind.MAIL, QrContent.of("mailto:anna@example.org").kind());
        assertEquals(QrContent.Kind.PHONE, QrContent.of("tel:+49301234").kind());
        assertEquals(QrContent.Kind.TEXT, QrContent.of("Hello").kind());
    }

    @Test
    void wifi() {
        QrContent c = QrContent.of("WIFI:T:WPA;S:Home Net;P:se\\;cr\\:et;H:false;;");
        assertEquals(QrContent.Kind.WIFI, c.kind());
        assertEquals("Home Net", c.wifiName());
        assertEquals("se;cr:et", c.wifiPassword());
        assertEquals("WPA", c.fields().get("T"));
    }

    @Test
    void wifiWithQuotesAndNoPassword() {
        QrContent c = QrContent.of("WIFI:S:\"Café\";T:nopass;;");
        assertEquals("Café", c.wifiName());
        assertEquals("", c.wifiPassword());
    }
}
