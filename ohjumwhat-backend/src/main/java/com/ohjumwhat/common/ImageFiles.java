package com.ohjumwhat.common;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * 사용자가 올린 사진(프로필 사진, 채팅 사진)을 읽고 다시 그려 JPEG로 만든다. 다시 그려 저장하므로 EXIF(위치 정보 등) 같은
 * 메타데이터와 이미지가 아닌 내용은 남지 않는다. JPEG·PNG만 받는다.
 */
public final class ImageFiles {

	/** 받는 사진의 최대 한 변(px). 브라우저는 이보다 작게 줄여 보낸다. 압축된 큰 그림을 풀다가 메모리가 넘치지 않게 풀기 전에 확인한다. */
	public static final int MAX_SOURCE_SIZE = 2048;

	private static final float QUALITY = 0.85f;

	private static final Set<String> FORMATS = Set.of("jpeg", "png");

	private static final String UNUSABLE = "이 사진은 쓸 수 없어요. 다른 사진을 골라 주세요.";

	private ImageFiles() {
	}

	/** JPEG·PNG를 읽는다. 형식이 다르거나 깨졌거나 한 변이 {@link #MAX_SOURCE_SIZE}보다 크면 400이다. */
	public static BufferedImage read(byte[] bytes) {
		try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw ApiException.badRequest(UNUSABLE);
			}
			ImageReader reader = readers.next();
			try {
				if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
					throw ApiException.badRequest(UNUSABLE);
				}
				reader.setInput(input, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width > MAX_SOURCE_SIZE || height > MAX_SOURCE_SIZE) {
					throw ApiException.badRequest("사진이 너무 커요. 다른 사진을 골라 주세요.");
				}
				if (width < 1 || height < 1) {
					throw ApiException.badRequest(UNUSABLE);
				}
				return reader.read(0);
			}
			finally {
				reader.dispose();
			}
		}
		catch (ApiException e) {
			throw e;
		}
		catch (IOException | RuntimeException e) {
			// 깨진 파일, CMYK JPEG처럼 읽지 못하는 그림
			throw ApiException.badRequest(UNUSABLE);
		}
	}

	/** JPEG에는 투명도가 없으므로 흰 바탕에 그린다. */
	public static BufferedImage flatten(BufferedImage image) {
		BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = rgb.createGraphics();
		try {
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
			g.drawImage(image, 0, 0, null);
		}
		finally {
			g.dispose();
		}
		return rgb;
	}

	/** 비율을 지키며 긴 변이 maxLongSide를 넘지 않게 줄인다. 이미 작으면 그대로 둔다(키우지 않는다). */
	public static BufferedImage fitWithin(BufferedImage image, int maxLongSide) {
		int width = image.getWidth();
		int height = image.getHeight();
		int longSide = Math.max(width, height);
		if (longSide <= maxLongSide) {
			return image;
		}
		double ratio = (double) maxLongSide / longSide;
		return scale(image, Math.max(1, (int) Math.round(width * ratio)), Math.max(1, (int) Math.round(height * ratio)));
	}

	/** width×height로 그린다. 크게 줄일 때는 반씩 여러 번 줄여야 한 번에 줄이는 것보다 덜 거칠다. */
	public static BufferedImage scale(BufferedImage image, int width, int height) {
		BufferedImage current = image;
		while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
			current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
		}
		return current.getWidth() == width && current.getHeight() == height ? current : draw(current, width, height);
	}

	/** JPEG(품질 0.85, 메타데이터 없음)로 만든다. RGB 그림을 받는다({@link #flatten}). */
	public static byte[] encode(BufferedImage image) {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(QUALITY);
			writer.setOutput(output);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		catch (IOException e) {
			throw new IllegalStateException("사진을 JPEG로 만들지 못했습니다.", e);
		}
		finally {
			writer.dispose();
		}
		return bytes.toByteArray();
	}

	private static BufferedImage draw(BufferedImage image, int width, int height) {
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = scaled.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(image, 0, 0, width, height, null);
		}
		finally {
			g.dispose();
		}
		return scaled;
	}
}
