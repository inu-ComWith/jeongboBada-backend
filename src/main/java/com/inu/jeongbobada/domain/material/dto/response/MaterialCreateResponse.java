package com.inu.jeongbobada.domain.material.dto.response;

// 업로드 성공 시 새 자료의 id (화면이 바로 상세로 이동하거나 목록을 새로 불러올 때 쓴다)
public record MaterialCreateResponse(
    Long materialId
) {
}
