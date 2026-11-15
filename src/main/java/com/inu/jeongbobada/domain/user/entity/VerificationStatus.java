package com.inu.jeongbobada.domain.user.entity;

// 재학생 인증 상태. 가입하면 PENDING, 관리자가 서류를 보고 APPROVED/REJECTED로 바꾼다 (#142)
public enum VerificationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
