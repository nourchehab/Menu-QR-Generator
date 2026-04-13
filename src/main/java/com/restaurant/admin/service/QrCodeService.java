package com.restaurant.admin.service;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeService {
    // Limit concurrent QR rendering to avoid CPU / memory spike under burst
    private static final Semaphore RENDER_SEMAPHORE = new Semaphore(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

    public byte[] generatePngQr(String text, int sizePx) {
        try {
            // Acquire a permit to bound concurrent CPU and memory usage during rendering
            try {
                RENDER_SEMAPHORE.acquire();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for QR render slot", ie);
            }

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, Integer.valueOf(1)); // small quiet zone

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        } finally {
            // Always release if we acquired
            if (RENDER_SEMAPHORE.availablePermits() < Math.max(1, Runtime.getRuntime().availableProcessors() / 2)) {
                RENDER_SEMAPHORE.release();
            }
        }
    }
}