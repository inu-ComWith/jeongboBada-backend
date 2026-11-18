package com.inu.jeongbobada.domain.user.exception;

import com.inu.jeongbobada.domain.user.dto.VerificationBlockedResponse;
import com.inu.jeongbobada.global.exception.BusinessException;
import lombok.Getter;

// 승인 대기·반려 계정의 로그인·토큰 재발급 거절. 화면이 팝업에 보여줄 값(신청일·반려 사유)을 함께 담는다 (#142)
@Getter
public class VerificationRequiredException extends BusinessException {

    private final VerificationBlockedResponse detail;

    public VerificationRequiredException(UserErrorCode errorCode, VerificationBlockedResponse detail) {
        super(errorCode);
        this.detail = detail;
    }
}
