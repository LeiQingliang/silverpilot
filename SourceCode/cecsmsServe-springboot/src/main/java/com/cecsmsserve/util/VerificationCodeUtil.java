package com.cecsmsserve.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;

/** Generates a short-lived human verification image. */
public class VerificationCodeUtil {
    private static final int WIDTH = 120;
    private static final int HEIGHT = 36;
    private static final String[] FONT_NAMES = { "微软雅黑", "黑体", "Arial", "SansSerif" };
    private static final Color BACKGROUND = new Color(248, 251, 252);
    private static final String CODES = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final SecureRandom random = new SecureRandom();
    private String text;

    /**
     * 获取一个随意颜色
     *
     * @return
     */
    private Color randomColor() {
        int red = random.nextInt(150);
        int green = random.nextInt(150);
        int blue = random.nextInt(150);
        return new Color(red, green, blue);
    }

    /**
     * 获取一个随机字体
     *
     * @return
     */
    private Font randomFont() {
        String name = FONT_NAMES[random.nextInt(FONT_NAMES.length)];
        int style = random.nextBoolean() ? Font.PLAIN : Font.BOLD;
        int size = random.nextInt(4) + 25;
        return new Font(name, style, size);
    }

    /**
     * 获取一个随机字符
     *
     * @return
     */
    private char randomChar() {
        return CODES.charAt(random.nextInt(CODES.length()));
    }

    /**
     * 创建一个空白的BufferedImage对象
     *
     * @return
     */
    private BufferedImage createImage() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(BACKGROUND);
        g2.fillRect(0, 0, WIDTH, HEIGHT);
        g2.dispose();
        return image;
    }

    public BufferedImage getImage() {
        BufferedImage image = createImage();
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            StringBuilder builder = new StringBuilder(4);
            for (int i = 0; i < 4; i++) {
                String value = String.valueOf(randomChar());
                builder.append(value);
                g2.setColor(randomColor());
                g2.setFont(randomFont());
                float x = 6 + i * (WIDTH - 10f) / 4;
                g2.drawString(value, x, HEIGHT - 8);
            }
            this.text = builder.toString();
            drawLines(g2);
        } finally {
            g2.dispose();
        }
        return image;
    }

    /**
     * 绘制干扰线
     *
     * @param image
     */
    private void drawLines(Graphics2D g2) {
        for (int i = 0; i < 3; i++) {
            int x1 = random.nextInt(WIDTH);
            int y1 = random.nextInt(HEIGHT);
            int x2 = random.nextInt(WIDTH);
            int y2 = random.nextInt(HEIGHT);
            g2.setColor(randomColor());
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawLine(x1, y1, x2, y2);
        }
    }

    public String getText() {
        return text;
    }

    public static void output(BufferedImage image, OutputStream out) throws IOException {
        if (!ImageIO.write(image, "JPEG", out)) {
            throw new IOException("No JPEG image writer is available");
        }
    }
}
