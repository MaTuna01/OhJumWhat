package com.ohjumwhat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/** 프로필 사진 테스트용 그림 */
public final class TestImages {

	private TestImages() {
	}

	/** 주황색 바탕의 JPEG */
	public static byte[] jpeg(int width, int height) {
		return write(filled(width, height, BufferedImage.TYPE_INT_RGB, new Color(249, 115, 22)), "jpeg");
	}

	/** 가운데에 빨간 사각형이 있고 나머지는 투명한 PNG */
	public static byte[] transparentPng(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(width / 4, height / 4, width / 2, height / 2);
		g.dispose();
		return write(image, "png");
	}

	/** 프로필 사진으로 받지 않는 형식 */
	public static byte[] gif(int width, int height) {
		return write(filled(width, height, BufferedImage.TYPE_INT_RGB, Color.BLUE), "gif");
	}

	/** 압축하면 아주 작지만 풀면 큰 그림(흑백 1비트) */
	public static byte[] hugePng(int size) {
		return write(new BufferedImage(size, size, BufferedImage.TYPE_BYTE_BINARY), "png");
	}

	public static BufferedImage read(byte[] bytes) {
		try {
			return ImageIO.read(new ByteArrayInputStream(bytes));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static BufferedImage filled(int width, int height, int type, Color color) {
		BufferedImage image = new BufferedImage(width, height, type);
		Graphics2D g = image.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, width, height);
		g.dispose();
		return image;
	}

	private static byte[] write(BufferedImage image, String format) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(image, format, bytes);
			return bytes.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
