package com.inu.jeongbobada.domain.user.dto;

import org.jspecify.annotations.Nullable;

// 관리자 서류 검토 화면. documentUrl은 5분 뒤 만료되는 서류 열람 주소이고, 서류가 없으면(처리 완료·기존 계정) null
public record VerificationDetailResponse(
    VerificationSummaryResponse applicant,
    @Nullable String documentUrl
) {
}
