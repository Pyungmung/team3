package com.customhouse.domain.listing.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [담당: 송귀성] 매물 등록 대표 사진 원본 (2026-10-06). 예전에는 서버 디스크(upload.dir)에 저장해서 Render 무료
 * 서버가 재배포/재시작되면 사진이 사라졌다 - DB(MEDIUMBLOB)에 저장해서 서버가 바뀌어도 남게 한다.
 * 사진 URL 형식(/uploads/listings/{filename})은 그대로라 프론트와 이미 등록된 카드는 바뀌지 않는다.
 */
@Entity
@Table(name = "listing_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListingPhoto extends BaseTimeEntity {

    @Id
    @Column(name = "filename", length = 80)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "data", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] data;

    public static ListingPhoto of(String filename, String contentType, byte[] data) {
        ListingPhoto photo = new ListingPhoto();
        photo.filename = filename;
        photo.contentType = contentType;
        photo.data = data;
        return photo;
    }
}
