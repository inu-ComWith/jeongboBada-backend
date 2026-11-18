package com.inu.jeongbobada.domain.user.service;

import com.inu.jeongbobada.domain.user.dto.VerificationDetailResponse;
import com.inu.jeongbobada.domain.user.dto.VerificationSummaryResponse;
import com.inu.jeongbobada.domain.user.entity.User;
import com.inu.jeongbobada.domain.user.entity.VerificationStatus;
import com.inu.jeongbobada.domain.user.exception.UserErrorCode;
import com.inu.jeongbobada.domain.user.repository.UserRepository;
import com.inu.jeongbobada.global.exception.BusinessException;
import com.inu.jeongbobada.global.file.FileErrorCode;
import com.inu.jeongbobada.global.file.FileStorage;
import com.inu.jeongbobada.global.mail.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.exception.SdkException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 관리자 재학생 인증 검토 (#142 2단계). 승인·거절하면 서류를 지우고 결과를 메일로 알린다.
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminVerificationService {

    // 열 때마다 새로 만들므로 짧게 둔다 (docs/student-verification-architecture.md)
    private static final Duration DOCUMENT_URL_EXPIRES_IN = Duration.ofMinutes(5);

    private static final String APPROVED_SUBJECT = "[정보바다] 가입이 승인되었어요";
    private static final String REJECTED_SUBJECT = "[정보바다] 가입 신청이 반려되었어요";

    private final UserRepository userRepository;
    private final FileStorage fileStorage;
    private final EmailSender emailSender;

    @Transactional(readOnly = true)
    public List<VerificationSummaryResponse> getList(VerificationStatus status) {
        List<User> users = status == VerificationStatus.PENDING
            ? userRepository.findPendingVerifications()
            : userRepository.findProcessedByVerificationStatus(status);
        return users.stream().map(VerificationSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VerificationDetailResponse getDetail(Long userId) {
        User user = findUser(userId);
        return new VerificationDetailResponse(VerificationSummaryResponse.from(user), documentUrlOf(user));
    }

    @Transactional
    public void approve(Long userId) {
        User user = findPendingUser(userId);
        String documentKey = user.getVerificationDocumentKey();
        user.approveVerification(LocalDateTime.now());
        afterCommit(documentKey, user.getEmail(), APPROVED_SUBJECT, approvedBody(user.getNickname()));
    }

    @Transactional
    public void reject(Long userId, @Nullable String reason) {
        User user = findPendingUser(userId);
        String documentKey = user.getVerificationDocumentKey();
        String trimmedReason = reason == null || reason.isBlank() ? null : reason.strip();
        user.rejectVerification(LocalDateTime.now(), trimmedReason);
        afterCommit(documentKey, user.getEmail(), REJECTED_SUBJECT, rejectedBody(user.getNickname(), trimmedReason));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    // 두 관리자가 같은 신청을 동시에 처리해도 한 번만 처리되도록 행을 잠그고 상태를 본다
    private User findPendingUser(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        if (user.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new BusinessException(UserErrorCode.VERIFICATION_ALREADY_PROCESSED);
        }
        return user;
    }

    private @Nullable String documentUrlOf(User user) {
        String key = user.getVerificationDocumentKey();
        if (key == null) {
            return null;
        }
        try {
            return fileStorage.presignedGetUrl(key, DOCUMENT_URL_EXPIRES_IN);
        } catch (SdkException e) {
            log.error("서류 열람 URL 생성 실패: key={}", key, e);
            throw new BusinessException(FileErrorCode.FILE_URL_FAILED);
        }
    }

    // 서류 삭제와 메일은 DB 처리가 커밋된 뒤에 한다. 커밋 전에 지우면 롤백됐을 때 서류만 사라진다.
    // 이미 처리는 끝났으므로 둘 다 실패해도 응답은 성공이고 로그만 남긴다 (서류는 key 로그를 보고 수동 정리)
    private void afterCommit(@Nullable String documentKey, @Nullable String email, String subject, String body) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteDocument(documentKey);
                sendResultMail(email, subject, body);
            }
        });
    }

    private void deleteDocument(@Nullable String documentKey) {
        if (documentKey == null) {
            return;
        }
        try {
            fileStorage.delete(documentKey);
        } catch (RuntimeException e) {
            log.error("검토 후 서류 삭제 실패: key={}", documentKey, e);
        }
    }

    // 이메일 기능 전에 가입한 계정은 이메일이 없을 수 있다
    private void sendResultMail(@Nullable String email, String subject, String body) {
        if (email == null) {
            return;
        }
        try {
            emailSender.send(email, subject, body);
        } catch (RuntimeException e) {
            log.error("가입 검토 결과 메일 발송 실패: subject={}", subject, e);
        }
    }

    private static String approvedBody(String nickname) {
        return """
            %s님, 정보바다 가입이 승인되었어요.
            이제 로그인해서 서비스를 이용할 수 있어요.

            제출하신 신분 확인 서류는 안내드린 대로 삭제했어요.
            """.formatted(nickname);
    }

    private static String rejectedBody(String nickname, @Nullable String reason) {
        String reasonLine = reason == null ? "" : "반려 사유: " + reason + "\n";
        return """
            %s님, 정보바다 가입 신청이 반려되었어요.
            %s
            제출하신 신분 확인 서류는 삭제했어요.
            문의가 있으면 운영진에게 연락해주세요.
            """.formatted(nickname, reasonLine);
    }
}
