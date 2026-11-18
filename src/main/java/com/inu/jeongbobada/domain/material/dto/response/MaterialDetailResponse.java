package com.inu.jeongbobada.domain.material.dto.response;

import com.inu.jeongbobada.domain.material.entity.Material;
import com.inu.jeongbobada.domain.material.enums.MaterialType;
import com.inu.jeongbobada.global.file.FileType;

import java.time.LocalDateTime;
import java.util.List;

// 학습자료 상세. 파일은 이름·형식·크기만 주고, 실제 파일은 download API로 받은 주소로 내려받는다
public record MaterialDetailResponse(
    Long materialId,
    String title,
    List<MaterialType> materialTypes,
    String nickname,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String content,
    String fileName,
    String fileType,
    long fileSize,
    boolean isMine
) {
    public static MaterialDetailResponse of(Material material, Long viewerId) {
        return new MaterialDetailResponse(
            material.getMaterialId(),
            material.getTitle(),
            material.getMaterialTypes().stream().sorted().toList(),
            material.getUser().getNickname(),
            material.getCreatedAt(),
            material.getUpdatedAt(),
            material.getContent(),
            material.getOriginalFileName(),
            FileType.extensionOfKey(material.getFileKey()),
            material.getFileSize(),
            material.isWrittenBy(viewerId)
        );
    }
}
