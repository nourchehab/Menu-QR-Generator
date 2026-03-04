package com.restaurant.admin.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class QrCodeServiceTest {

    @Test
    public void generatePngQr_validInput_returnsNonEmptyPngBytes() {
        // create the service and sample input
        QrCodeService service = new QrCodeService();
        String text = "hello";
        int sizePx = 200;

        // call the method we want to test
        byte[] pngBytes = service.generatePngQr(text, sizePx);

        // make sure something was returned
        assertNotNull(pngBytes);
        assertTrue(pngBytes.length > 0);

        // check that the result is actually a PNG file (PNG files start with these bytes)
        byte[] expectedHeader = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };

        assertTrue(pngBytes.length >= 8);

        int i = 0;
        while (i < 8) {
            assertEquals(expectedHeader[i], pngBytes[i]);
            i++;
        }
    }

    @Test
    public void generatePngQr_nullText_throwsRuntimeException() {
        QrCodeService service = new QrCodeService();

        // passing null should cause an exception
        assertThrows(RuntimeException.class, () -> service.generatePngQr(null, 200));
    }

    @Test
    public void generatePngQr_invalidSize_throwsRuntimeException() {
        QrCodeService service = new QrCodeService();

        // size 0 is not valid, so the method should throw an exception
        assertThrows(RuntimeException.class, () -> service.generatePngQr("test", 0));
    }
}