package com.inu.jeongbobada.domain.material.dto.response;

import com.inu.jeongbobada.domain.material.entity.Material;
import com.inu.jeongbobada.global.file.FileType;

import java.time.LocalDateTime;

// 학습자료 목록 한 줄. fileType은 목록의 "형식" 칸(pdf 등), isMine은 삭제 버튼 표시용
public record MaterialSummaryResponse(
    Long materialId,
    String title,
    String nickname,
    String fileType,
    LocalDateTime createdAt,
    boolean isMine
) {
    public static MaterialSummaryResponse of(Material material, Long viewerId) {
        return new MaterialSummaryResponse(
            material.getMaterialId(),
            material.getTitle(),
            material.getUser().getNickname(),
            FileType.extensionOfKey(material.getFileKey()),
            material.getCreatedAt(),
            material.isWrittenBy(viewerId)
        );
    }
}
