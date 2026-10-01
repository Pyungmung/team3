package com.customhouse.domain.listing.controller;

import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import com.customhouse.global.jwt.AuthenticatedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * [담당: 송귀성] 매물 등록 대표 사진 업로드. "/api/listings/**"가 SecurityConfig에서 이미
 * authenticated()라 로그인한 회원만 호출 가능하다. 로컬 디스크(upload.dir)에 저장하고
 * WebMvcConfig가 /uploads/**로 정적 서빙하는 URL을 돌려주면, 프론트가 그 URL을 매물 등록
 * payload의 photoUrl에 실어 보낸다.
 */
@Slf4j
@RestController
@RequestMapping("/api/listings/photos")
public class ListingPhotoController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    @Value("${upload.dir}")
    private String uploadDir;

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
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "jpg/png/webp 형식의 사진만 올릴 수 있어요.");
        }

        try {
            Path dir = Path.of(uploadDir, "listings");
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + "." + extension;
            Path target = dir.resolve(filename);
            file.transferTo(target);
            String photoUrl = "/uploads/listings/" + filename;
            return ResponseEntity.ok(ApiResponse.ok("사진이 업로드되었습니다.", Map.of("photoUrl", photoUrl)));
        } catch (IOException e) {
            log.error("매물 사진 업로드 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, "사진을 저장하지 못했어요. 잠시 후 다시 시도해주세요.");
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
