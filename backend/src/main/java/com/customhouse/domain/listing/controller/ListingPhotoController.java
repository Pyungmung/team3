package com.customhouse.domain.listing.controller;

import com.customhouse.domain.listing.entity.ListingPhoto;
import com.customhouse.domain.listing.repository.ListingPhotoRepository;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import com.customhouse.global.jwt.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * [담당: 송귀성] 매물 등록 대표 사진 업로드. "/api/listings/**"가 SecurityConfig에서 이미 authenticated()라
 * 로그인한 회원만 호출 가능하다. 사진은 DB(listing_photos)에 저장하고(2026-10-06 - 서버 디스크는 재배포 때 사라졌다),
 * ListingPhotoServeController가 /uploads/listings/{파일명}으로 내려주는 URL을 돌려준다. 프론트는 그 URL을 매물 등록
 * payload의 photoUrl에 실어 보낸다.
 */
@Slf4j
@RestController
@RequestMapping("/api/listings/photos")
@RequiredArgsConstructor
public class ListingPhotoController {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private final ListingPhotoRepository photoRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file
    ) {
        if (file.isEmpty()) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "사진 파일을 선택해주세요.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "파일 용량이 너무 큽니다 (5MB 이하만 가능해요).");
        }

        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(originalName);
        String contentType = CONTENT_TYPES.get(extension);
        if (contentType == null) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "jpg/png/webp 형식의 사진만 올릴 수 있어요.");
        }

        try {
            String filename = UUID.randomUUID() + "." + extension;
            photoRepository.save(ListingPhoto.of(filename, contentType, file.getBytes()));
            String photoUrl = "/uploads/listings/" + filename;
            return ResponseEntity.ok(ApiResponse.ok("사진이 업로드되었습니다.", Map.of("photoUrl", photoUrl)));
        } catch (IOException e) {
            log.error("매물 사진 업로드 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, "사진을 저장하지 못했어요. 잠시 후 다시 시도해주세요.");
        }
    }

    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
