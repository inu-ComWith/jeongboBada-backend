package com.inu.jeongbobada.domain.material.dto.response;

// 내려받기 주소. 5분 뒤 만료되므로 받자마자 연다. 원래 파일명으로 저장된다
public record MaterialDownloadResponse(
    String downloadUrl
) {
}
