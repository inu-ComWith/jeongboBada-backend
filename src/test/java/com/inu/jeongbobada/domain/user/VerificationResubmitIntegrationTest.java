package com.inu.jeongbobada.domain.user;

import com.inu.jeongbobada.domain.user.entity.User;
import com.inu.jeongbobada.domain.user.entity.VerificationStatus;
import com.inu.jeongbobada.domain.user.repository.UserRepository;
import com.inu.jeongbobada.global.file.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #142 3단계: 반려된 사용자가 학번 + 비밀번호 + 새 서류로 다시 신청한다 (반려 사용자는 로그인 토큰이 없다).
// 비밀번호 확인은 로그인과 같은 실패 횟수를 써야 이 API로 로그인 잠금을 우회하지 못한다.
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class VerificationResubmitIntegrationTest {

    private static final String PASSWORD = "password1";
    private static final String FIRST_KEY = "verification/first.pdf";
    private static final String NEW_KEY = "verification/resubmitted.pdf";

    @MockitoBean
    private FileStorage fileStorage;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String studentId;

    @BeforeEach
    void signUpAndReject() throws Exception {
        when(fileStorage.upload(anyString(), any(), any(), any())).thenReturn(FIRST_KEY, NEW_KEY);
        int n = ThreadLocalRandom.current().nextInt(100_000_000);
        studentId = String.format("9%08d", n);
        mockMvc.perform(SignupRequests.signup(studentId, PASSWORD, "rs" + (n % 10_000), "rs" + n + "@example.com"))
            .andExpect(status().isCreated());
        // 관리자 반려와 같은 상태로 만든다 (반려하면 서류는 지워지고 사유·처리 시각이 남는다)
        jdbcTemplate.update("""
            update users set verification_status = 'REJECTED', verification_document_key = null,
                rejection_reason = '학번이 보이지 않아요', verification_processed_at = now(),
                verification_requested_at = now() - interval '3 days'
            where student_id = ?
            """, studentId);
        // 가입 때 한 업로드는 빼고, 이 테스트에서 일어난 호출만 본다
        clearInvocations(fileStorage);
    }

    @AfterEach
    void cleanUp() {
        userRepository.findByStudentId(studentId).ifPresent(userRepository::delete);
    }

    @Test
    void 반려된_사용자가_새_서류로_다시_신청하면_승인_대기가_된다() throws Exception {
        LocalDateTime before = LocalDateTime.now().minusSeconds(5);

        mockMvc.perform(resubmit(studentId, PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        User user = userRepository.findByStudentId(studentId).orElseThrow();
        assertThat(user.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(user.getVerificationDocumentKey()).isEqualTo(NEW_KEY);
        assertThat(user.getRejectionReason()).isNull();
        assertThat(user.getVerificationProcessedAt()).isNull();
        assertThat(user.getVerificationRequestedAt()).isAfter(before);
    }

    @Test
    void 비밀번호가_틀리면_401이고_로그인과_같은_실패_횟수에_쌓이며_서류를_올리지_않는다() throws Exception {
        mockMvc.perform(resubmit(studentId, "wrongPassword1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.message").value("학번 또는 비밀번호가 일치하지 않아요 (1/5회)"));

        User user = userRepository.findByStudentId(studentId).orElseThrow();
        assertThat(user.getLoginFailedAttempts()).isEqualTo(1);
        assertThat(user.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        verify(fileStorage, never()).upload(eq("verification"), any(), any(), any());
    }

    @Test
    void 반려_상태가_아니면_409() throws Exception {
        jdbcTemplate.update("update users set verification_status = 'PENDING' where student_id = ?", studentId);

        mockMvc.perform(resubmit(studentId, PASSWORD))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERIFICATION_NOT_REJECTED"));
    }

    private MockMultipartHttpServletRequestBuilder resubmit(String studentId, String password) {
        return multipart("/api/auth/verification/resubmit")
            .file(SignupRequests.FAKE_PDF)
            .param("studentId", studentId)
            .param("password", password);
    }
}
