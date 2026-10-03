package com.ohjumwhat.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.ohjumwhat.TestImages;
import com.ohjumwhat.common.ApiException;

class ProfilePhotoImagesTest {

	@Test
	void 사진을_가운데_정사각형으로_잘라_256px_JPEG로_만들고_투명한_곳은_흰색으로_채운다() {
		byte[] jpeg = ProfilePhotoImages.toAvatarJpeg(TestImages.transparentPng(600, 400));

		BufferedImage image = TestImages.read(jpeg);
		assertThat(image.getWidth()).isEqualTo(ProfilePhotoImages.SIZE);
		assertThat(image.getHeight()).isEqualTo(ProfilePhotoImages.SIZE);
		// 가운데(빨강)는 남고, 모서리(투명)는 흰색이 된다. JPEG라 색이 조금 달라질 수 있다.
		int center = image.getRGB(128, 128);
		assertThat((center >> 16) & 0xff).isGreaterThan(200);
		assertThat(center & 0xff).isLessThan(60);
		int corner = image.getRGB(2, 2);
		assertThat(corner & 0xffffff).isGreaterThan(0xf0f0f0);
	}

	@Test
	void 작은_사진은_256px로_키우고_JPEG도_받는다() {
		BufferedImage image = TestImages.read(ProfilePhotoImages.toAvatarJpeg(TestImages.jpeg(100, 120)));

		assertThat(image.getWidth()).isEqualTo(256);
		assertThat(image.getHeight()).isEqualTo(256);
	}

	@Test
	void 다시_만든_JPEG에는_EXIF_같은_메타데이터가_없다() {
		byte[] jpeg = ProfilePhotoImages.toAvatarJpeg(TestImages.jpeg(512, 512));

		assertThat(new String(jpeg, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
	}

	@Test
	void 이미지가_아니면_거절한다() {
		assertThatThrownBy(() -> ProfilePhotoImages.toAvatarJpeg("<svg onload=alert(1)>".getBytes()))
			.isInstanceOf(ApiException.class)
			.hasMessage("이 사진은 쓸 수 없어요. 다른 사진을 골라 주세요.");
		assertThatThrownBy(() -> ProfilePhotoImages.toAvatarJpeg(new byte[0])).isInstanceOf(ApiException.class);
	}

	@Test
	void 너무_큰_그림은_풀기_전에_거절한다() {
		byte[] huge = TestImages.hugePng(3000);
		assertThat(huge.length).isLessThan(100_000);

		assertThatThrownBy(() -> ProfilePhotoImages.toAvatarJpeg(huge))
			.isInstanceOf(ApiException.class)
			.hasMessage("사진이 너무 커요. 다른 사진을 골라 주세요.");
	}

	@Test
	void 지원하지_않는_형식은_거절한다() {
		byte[] gif = TestImages.gif(10, 10);

		assertThatThrownBy(() -> ProfilePhotoImages.toAvatarJpeg(gif)).isInstanceOf(ApiException.class);
	}
}
