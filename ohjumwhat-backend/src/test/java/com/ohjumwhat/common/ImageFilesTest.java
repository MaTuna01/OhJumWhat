package com.ohjumwhat.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.junit.jupiter.api.Test;

import com.ohjumwhat.TestImages;

class ImageFilesTest {

	@Test
	void 비율을_지키며_긴_변을_줄이고_작은_그림은_그대로_둔다() {
		BufferedImage wide = ImageFiles.read(TestImages.jpeg(2000, 500));
		BufferedImage fitted = ImageFiles.fitWithin(wide, 1600);
		assertThat(fitted.getWidth()).isEqualTo(1600);
		assertThat(fitted.getHeight()).isEqualTo(400);

		BufferedImage tall = ImageFiles.fitWithin(ImageFiles.read(TestImages.jpeg(300, 1201)), 480);
		assertThat(tall.getWidth()).isEqualTo(120);
		assertThat(tall.getHeight()).isEqualTo(480);

		BufferedImage small = ImageFiles.read(TestImages.jpeg(100, 80));
		assertThat(ImageFiles.fitWithin(small, 480)).isSameAs(small);
	}

	@Test
	void 아주_길쭉한_그림도_한_변이_0이_되지_않는다() {
		BufferedImage line = ImageFiles.fitWithin(ImageFiles.read(TestImages.jpeg(2000, 1)), 480);
		assertThat(line.getWidth()).isEqualTo(480);
		assertThat(line.getHeight()).isEqualTo(1);
	}

	@Test
	void 다시_그린_JPEG에는_메타데이터가_없다() throws Exception {
		byte[] jpeg = ImageFiles.encode(ImageFiles.flatten(ImageFiles.read(TestImages.transparentPng(64, 48))));
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
			ImageReader reader = ImageIO.getImageReaders(input).next();
			reader.setInput(input);
			assertThat(reader.getFormatName().toLowerCase(Locale.ROOT)).isEqualTo("jpeg");
			assertThat(reader.getWidth(0)).isEqualTo(64);
			// EXIF(APP1) 마커가 없다
			assertThat(new String(jpeg, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
			reader.dispose();
		}
	}
}
