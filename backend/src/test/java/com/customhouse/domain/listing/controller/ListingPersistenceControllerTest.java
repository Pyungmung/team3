package com.customhouse.domain.listing.controller;

import com.customhouse.domain.listing.entity.ListingPhoto;
import com.customhouse.domain.listing.entity.RegisteredListing;
import com.customhouse.domain.listing.repository.ListingPhotoRepository;
import com.customhouse.domain.listing.repository.RegisteredListingRepository;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.error.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [담당: 송귀성] 등록 매물/사진 DB 저장 - AI 엔진 전용 내부 API(비밀키 검사, 저장된 원본 행 반환)와 사진 DB 업로드/서빙(디스크 폴백,
 * 경로 조작 차단)을 확인한다. 서버가 재시작돼도 매물과 사진이 DB에서 되살아나는 구조의 백엔드 쪽 부분.
 */
class ListingPersistenceControllerTest {

    private RegisteredListingRepository registeredRepo;
    private ListingPhotoRepository photoRepo;
    private InternalListingController internal;
    private ListingPhotoController uploader;
    private ListingPhotoServeController server;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        registeredRepo = mock(RegisteredListingRepository.class);
        photoRepo = mock(ListingPhotoRepository.class);
        internal = new InternalListingController(registeredRepo);
        ReflectionTestUtils.setField(internal, "internalApiKey", "secret");
        uploader = new ListingPhotoController(photoRepo);
        server = new ListingPhotoServeController(photoRepo);
        ReflectionTestUtils.setField(server, "uploadDir", tmp.toString());
    }

    // ---- 내부 API ----

    @Test
    void 비밀키가_맞으면_저장된_매물_원본_행을_돌려주고_원본이_없는_옛_기록은_건너뛴다() {
        when(registeredRepo.findAll()).thenReturn(List.of(
                RegisteredListing.of("SEOCHO-202610-0001", 1L, "서초구", "{\"매물등록번호\":\"SEOCHO-202610-0001\",\"자치구\":\"서초구\"}"),
                RegisteredListing.of("SEOCHO-202610-0002", 1L, "서초구"),
                RegisteredListing.of("SEOCHO-202610-0003", 1L, "서초구", "깨진 JSON {")));

        ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = internal.registeredListings("secret");

        List<Map<String, Object>> rows = response.getBody().data();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("매물등록번호", "SEOCHO-202610-0001").containsEntry("자치구", "서초구");
    }

    @Test
    void 비밀키가_없거나_틀리면_거절한다() {
        assertThatThrownBy(() -> internal.registeredListings(null)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> internal.registeredListings("wrong")).isInstanceOf(CustomException.class);
    }

    @Test
    void 서버에_비밀키가_설정돼_있지_않으면_빈_키로도_항상_거절한다() {
        ReflectionTestUtils.setField(internal, "internalApiKey", "");
        assertThatThrownBy(() -> internal.registeredListings("")).isInstanceOf(CustomException.class);
    }

    // ---- 사진 ----

    @Test
    void 사진을_올리면_DB에_저장하고_서빙_URL을_돌려준다() {
        MockMultipartFile file = new MockMultipartFile("file", "room.JPG", "image/jpeg", new byte[]{1, 2, 3});

        Map<String, String> data = uploader.upload(null, file).getBody().data();

        ArgumentCaptor<ListingPhoto> saved = ArgumentCaptor.forClass(ListingPhoto.class);
        verify(photoRepo).save(saved.capture());
        assertThat(saved.getValue().getContentType()).isEqualTo("image/jpeg");
        assertThat(saved.getValue().getData()).containsExactly(1, 2, 3);
        assertThat(data.get("photoUrl")).isEqualTo("/uploads/listings/" + saved.getValue().getFilename()).endsWith(".jpg");
    }

    @Test
    void 지원하지_않는_형식이나_빈_파일은_거절한다() {
        assertThatThrownBy(() -> uploader.upload(null, new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1})))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> uploader.upload(null, new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void DB에_있는_사진은_그대로_내려준다() {
        when(photoRepo.findById("a.png")).thenReturn(Optional.of(ListingPhoto.of("a.png", "image/png", new byte[]{9, 8})));

        ResponseEntity<byte[]> response = server.serve("a.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(response.getBody()).containsExactly(9, 8);
        assertThat(response.getHeaders().getCacheControl()).contains("max-age");
    }

    @Test
    void DB에_없으면_예전_디스크_파일로_대신하고_둘_다_없으면_404() throws Exception {
        when(photoRepo.findById(any())).thenReturn(Optional.empty());
        Path dir = Files.createDirectories(Path.of((String) ReflectionTestUtils.getField(server, "uploadDir"), "listings"));
        Files.write(dir.resolve("old.jpg"), new byte[]{7});

        assertThat(server.serve("old.jpg").getBody()).containsExactly(7);
        assertThat(server.serve("none.jpg").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 경로를_거슬러_올라가는_파일_이름은_막는다() {
        assertThat(server.serve("..").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(server.serve("a..b.jpg").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(server.serve("a b.jpg").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
