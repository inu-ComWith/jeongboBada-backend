package com.inu.jeongbobada.domain.material.dto.response;

import java.util.List;

// 무한 스크롤용 목록 응답. totalCount는 "자료 n개" 표시, hasNext가 false면 더 불러오지 않는다
public record MaterialPageResponse(
    long totalCount,
    boolean hasNext,
    List<MaterialSummaryResponse> materials
) {
}
