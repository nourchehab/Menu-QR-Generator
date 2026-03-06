package com.restaurant.admin.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeService {

    public byte[] generatePngQr(String text, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, Integer.valueOf(1)); // small quiet zone

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            BufferedImage image = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_RGB);

            for (int y = 0; y < sizePx; y++) {
                for (int x = 0; x < sizePx; x++) {
                    int rgb = matrix.get(x, y) ? 0x000000 : 0xFFFFFF;
                    image.setRGB(x, y, rgb);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}