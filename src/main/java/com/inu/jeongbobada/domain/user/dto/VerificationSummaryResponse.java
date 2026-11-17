package com.inu.jeongbobada.domain.user.dto;

import com.inu.jeongbobada.domain.user.entity.User;
import com.inu.jeongbobada.domain.user.entity.VerificationStatus;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

// 관리자 가입 검토 목록 한 줄 (대기·승인·거절 목록 공통). 관리자 전용이라 학번·이메일을 내려준다.
// documentType은 대기 목록용(처리하면 서류가 지워져 null), processedAt은 승인일/거절일, rejectionReason은 거절 목록용
public record VerificationSummaryResponse(
    Long userId,
    String nickname,
    String studentId,
    @Nullable String email,
    LocalDateTime appliedAt,
    VerificationStatus status,
    @Nullable String documentType,
    @Nullable LocalDateTime processedAt,
    @Nullable String rejectionReason
) {
    public static VerificationSummaryResponse from(User user) {
        return new VerificationSummaryResponse(
            user.getUserId(),
            user.getNickname(),
            user.getStudentId(),
            user.getEmail(),
            user.getCreatedAt(),
            user.getVerificationStatus(),
            documentTypeOf(user.getVerificationDocumentKey()),
            user.getVerificationProcessedAt(),
            user.getRejectionReason()
        );
    }

    // key는 {prefix}/{UUID}.{확장자}라 확장자가 곧 형식이다 (FileStorage)
    private static @Nullable String documentTypeOf(@Nullable String key) {
        return key == null ? null : key.substring(key.lastIndexOf('.') + 1);
    }
}
