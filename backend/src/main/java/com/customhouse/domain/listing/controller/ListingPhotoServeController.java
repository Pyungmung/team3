package com.customhouse.domain.listing.controller;

import com.customhouse.domain.listing.entity.ListingPhoto;
import com.customhouse.domain.listing.repository.ListingPhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * [담당: 송귀성] 매물 사진을 /uploads/listings/{파일명}으로 내려준다 (SecurityConfig의 anyRequest().permitAll()에 걸려
 * 인증 없이 공개 - 카드에 사진이 뜨려면 누구나 볼 수 있어야 한다). 원본은 DB(listing_photos)에서 읽는다.
 * DB에 없으면 예전 방식(서버 디스크 upload.dir)에 남아 있는 파일을 대신 보여준다 - 로컬 개발에서 DB 저장 이전에
 * 올린 사진을 위한 것이고, 둘 다 없으면 404다. 파일 이름은 영문/숫자/점/하이픈/밑줄만 허용(경로 조작 방지).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ListingPhotoServeController {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    private final ListingPhotoRepository photoRepository;

    @Value("${upload.dir}")
    private String uploadDir;

    @GetMapping("/uploads/listings/{filename:.+}")
    public ResponseEntity<byte[]> serve(@PathVariable String filename) {
        if (!SAFE_NAME.matcher(filename).matches() || filename.contains("..")) {
            return ResponseEntity.notFound().build();
        }
        Optional<ListingPhoto> stored = photoRepository.findById(filename);
        if (stored.isPresent()) {
            return image(stored.get().getContentType(), stored.get().getData());
        }
        // 폴백: 예전 디스크 저장 파일
        try {
            Path path = Path.of(uploadDir, "listings", filename);
            if (Files.isRegularFile(path)) {
                return image(contentTypeOf(filename), Files.readAllBytes(path));
            }
        } catch (IOException e) {
            log.warn("디스크 사진을 읽지 못했습니다 ({}): {}", filename, e.toString());
        }
        return ResponseEntity.notFound().build();
    }

    private static ResponseEntity<byte[]> image(String contentType, byte[] data) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(data);
    }

    static String contentTypeOf(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
