package com.inu.jeongbobada.domain.user.service;

import com.inu.jeongbobada.domain.user.dto.LoginRequest;
import com.inu.jeongbobada.domain.user.dto.NicknameCheckResponse;
import com.inu.jeongbobada.domain.user.dto.ReissueRequest;
import com.inu.jeongbobada.domain.user.dto.SignupRequest;
import com.inu.jeongbobada.domain.user.dto.TokenResponse;
import com.inu.jeongbobada.domain.user.dto.VerificationBlockedResponse;
import com.inu.jeongbobada.domain.user.dto.VerificationResubmitRequest;
import com.inu.jeongbobada.domain.user.entity.User;
import com.inu.jeongbobada.domain.user.entity.VerificationStatus;
import com.inu.jeongbobada.domain.user.exception.LoginLockedException;
import com.inu.jeongbobada.domain.user.exception.UserErrorCode;
import com.inu.jeongbobada.domain.user.exception.VerificationRequiredException;
import com.inu.jeongbobada.domain.user.repository.UserRepository;
import com.inu.jeongbobada.global.exception.BusinessException;
import com.inu.jeongbobada.global.file.FileStorage;
import com.inu.jeongbobada.global.file.FileType;
import com.inu.jeongbobada.global.security.JwtProperties;
import com.inu.jeongbobada.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String VERIFICATION_PREFIX = "verification";
    private static final Set<FileType> VERIFICATION_DOCUMENT_TYPES = Set.of(FileType.PDF, FileType.JPG);
    private static final DataSize VERIFICATION_DOCUMENT_MAX_SIZE = DataSize.ofMegabytes(10);

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final FileStorage fileStorage;

    // 학번/닉네임/이메일 중복 확인 → 재학 증빙 서류 S3 업로드 → 승인 대기(PENDING)로 저장.
    // 중복이면 업로드하지 않는다. S3는 DB 트랜잭션에 묶이지 않아서, 저장이 실패하면 올린 서류를 직접 지운다.
    @Transactional
    public void signup(SignupRequest request) {
        //학번 중복 확인
        if (userRepository.findByStudentId(request.studentId()).isPresent()) {
            throw new BusinessException(UserErrorCode.DUPLICATE_STUDENT_ID);
        }
        //닉네임 중복확인
        if (userRepository.findByNickname(request.nickname()).isPresent()) {
            throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
        }
        //이메일 중복확인 (비밀번호 찾기에서 이메일로 계정을 특정하므로 unique)
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }
        //DB에 저장할때 해쉬값으로 바꿔서 저장
        String encodedPassword = passwordEncoder.encode(request.password());

        String documentKey = fileStorage.upload(VERIFICATION_PREFIX, request.file(),
            VERIFICATION_DOCUMENT_TYPES, VERIFICATION_DOCUMENT_MAX_SIZE);
        User user = User.create(
            request.studentId(),
            encodedPassword,
            request.nickname(),
            null, // department: 회원가입 요청에 아직 없는 값이라 일단 null
            email,
            documentKey
        );

        // flush까지 해야 UNIQUE 충돌(동시 가입, #109)이 여기서 드러나 서류를 지울 수 있다
        try {
            userRepository.saveAndFlush(user);
        } catch (RuntimeException e) {
            deleteUploadedDocument(documentKey, e);
            throw e;
        }
    }

    // 원래 예외(409 등)를 살려야 하므로 삭제 실패는 로그만 남긴다. 남은 서류는 로그의 key를 보고 지운다
    // (AWS S3에서는 수명주기 규칙이 30일 뒤 지운다. Supabase Storage는 수명주기 규칙이 없다)
    private void deleteUploadedDocument(String documentKey, RuntimeException cause) {
        try {
            fileStorage.delete(documentKey);
        } catch (RuntimeException deleteFailure) {
            cause.addSuppressed(deleteFailure);
            log.error("가입 실패 후 서류 삭제 실패: key={}", documentKey, deleteFailure);
        }
    }

    // 가입 폼에서 제출 전 미리 확인하는 용도. 최종 중복 검사는 signup()에서 다시 함(여기서 통과해도 그 사이 다른 사람이 선점할 수 있음)
    @Transactional(readOnly = true)
    public NicknameCheckResponse checkNicknameAvailability(String nickname) {
        return new NicknameCheckResponse(!userRepository.existsByNickname(nickname));
    }

    // 학번+비밀번호 검증(AuthenticationManager에 위임) 통과하면 access/refresh 토큰 새로 발급.
    // 틀린 횟수는 예외가 나도 커밋돼야 해서 BusinessException으로 롤백하지 않는다.
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse login(LoginRequest request) {
        User user = authenticate(request.studentId(), request.password());
        // 비밀번호 확인 뒤에 본다. 앞에서 보면 비밀번호를 몰라도 "이 학번은 승인 대기 중"이라는 정보가 새어 나간다 (#142)
        requireApproved(user);
        return issueTokens(user);
    }

    // 승인 대기·반려면 토큰을 주지 않는다 (#142 4단계). 화면이 팝업과 서류 다시 올리기 화면에 보여줄 신청일·반려 사유·닉네임을 함께 담는다
    private static void requireApproved(User user) {
        switch (user.getVerificationStatus()) {
            case APPROVED -> { }
            case PENDING -> throw new VerificationRequiredException(UserErrorCode.VERIFICATION_PENDING,
                VerificationBlockedResponse.pending(user.getVerificationRequestedAt()));
            case REJECTED -> throw new VerificationRequiredException(UserErrorCode.VERIFICATION_REJECTED,
                VerificationBlockedResponse.rejected(user.getRejectionReason(), user.getNickname()));
        }
    }

    // 반려된 사용자가 새 서류로 다시 신청한다 (#142 3단계). 반려 사용자는 로그인 토큰이 없어서 학번 + 비밀번호로 본인을 확인한다.
    // 비밀번호 확인은 로그인과 같은 실패 횟수·잠금을 쓴다. 따로 두면 이 API로 로그인 잠금을 우회해 비밀번호를 계속 시도할 수 있다.
    // 실패 횟수는 예외가 나도 커밋돼야 해서 BusinessException으로 롤백하지 않는다 (상태 변경은 모든 검사를 통과한 뒤에만 한다)
    @Transactional(noRollbackFor = BusinessException.class)
    public void resubmitVerification(VerificationResubmitRequest request) {
        User user = authenticate(request.studentId(), request.password());
        if (user.getVerificationStatus() != VerificationStatus.REJECTED) {
            throw new BusinessException(UserErrorCode.VERIFICATION_NOT_REJECTED);
        }

        String documentKey = fileStorage.upload(VERIFICATION_PREFIX, request.file(),
            VERIFICATION_DOCUMENT_TYPES, VERIFICATION_DOCUMENT_MAX_SIZE);
        user.resubmitVerification(documentKey, LocalDateTime.now());
        try {
            userRepository.saveAndFlush(user);
        } catch (RuntimeException e) {
            deleteUploadedDocument(documentKey, e);
            throw e;
        }
    }

    // 학번·잠금·비밀번호를 확인한다. 틀리면 실패 횟수를 올리고, 5번째면 잠근다 (로그인과 재신청이 같이 쓴다)
    private User authenticate(String studentId, String password) {
        User user = userRepository.findByStudentIdForUpdate(studentId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.STUDENT_ID_NOT_REGISTERED));

        LocalDateTime now = LocalDateTime.now();
        if (user.getLoginLockedUntil() != null && now.isBefore(user.getLoginLockedUntil())) {
            throw lockedException(now, user.getLoginLockedUntil());
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(studentId, password));
        } catch (BadCredentialsException e) {
            throw recordLoginFailure(user, now);
        }

        user.clearLoginFailures();
        return user;
    }

    private BusinessException recordLoginFailure(User user, LocalDateTime now) {
        int failedAttempts = user.recordLoginFailure();
        if (failedAttempts >= MAX_LOGIN_FAILURES) {
            LocalDateTime lockedUntil = now.plus(LOGIN_LOCK_DURATION);
            user.lockLogin(lockedUntil);
            return lockedException(now, lockedUntil);
        }
        return new BusinessException(
            UserErrorCode.INVALID_CREDENTIALS,
            "%s (%d/%d회)".formatted(UserErrorCode.INVALID_CREDENTIALS.getMessage(), failedAttempts, MAX_LOGIN_FAILURES)
        );
    }

    // 초 단위로 내리면 남은 시간이 0이 될 수 있어 올림한다
    private LoginLockedException lockedException(LocalDateTime now, LocalDateTime lockedUntil) {
        return new LoginLockedException(Duration.between(now, lockedUntil).plusSeconds(1).minusNanos(1).toSeconds());
    }

    // 클라이언트가 보낸 refresh token을 검증(서명/만료 + DB 저장값 일치)하고, 통과하면 토큰 재발급
    @Transactional
    public TokenResponse reissue(ReissueRequest request) {
        String refreshToken = request.refreshToken();

        User user = jwtTokenProvider.resolveStudentId(refreshToken)
            .flatMap(userRepository::findByStudentId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN));

        // DB에 저장된 값과 일치하는지, 만료 안 됐는지 확인
        if (user.getRefreshToken() == null
            || !user.getRefreshToken().equals(refreshToken)
            || user.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }
        // 로그인만 막으면, 차단 전에 받은 refresh token으로 14일 동안 계속 재발급받을 수 있다
        requireApproved(user);

        return issueTokens(user);
    }

    // 로그아웃: refresh token(권장) 또는 access token(예전 방식)으로 사용자를 찾아 DB의 refresh token을 제거(무효화)
    @Transactional
    public void logout(@Nullable String refreshToken, @Nullable String accessToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            revokeByRefreshToken(refreshToken);
            return;
        }
        if (accessToken == null) {
            return;
        }
        // 만료·무효한 access token이면 사용자를 특정할 수 없어 조용히 종료 (refresh 방식을 권장하는 이유)
        jwtTokenProvider.resolveAccessTokenStudentId(accessToken)
            .flatMap(userRepository::findByStudentId)
            .ifPresent(User::clearRefreshToken);
    }

    // refresh token으로 로그아웃: access token 만료 여부와 상관없이 지운다 (#129).
    // DB에 저장된 값과 같을 때만 지운다 — 이미 교체된 예전 refresh로 요청하면, 그 사이 새로 로그인한
    // 다른 기기의 현재 refresh까지 지워버리는 일을 막기 위해서다.
    private void revokeByRefreshToken(String refreshToken) {
        // 만료·위조된 refresh는 어차피 재발급에 쓸 수 없어 조용히 종료
        jwtTokenProvider.resolveStudentId(refreshToken)
            .flatMap(userRepository::findByStudentId)
            .filter(user -> refreshToken.equals(user.getRefreshToken()))
            .ifPresent(User::clearRefreshToken);
    }

    // access/refresh 토큰을 새로 만들고, refresh token은 만료시각과 함께 유저 엔티티에 저장
    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getStudentId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getStudentId());

        LocalDateTime expiresAt = LocalDateTime.now()
            .plus(Duration.ofMillis(jwtProperties.refreshExpiration()));
        user.updateRefreshToken(refreshToken, expiresAt);

        return new TokenResponse(accessToken, refreshToken);
    }
}


