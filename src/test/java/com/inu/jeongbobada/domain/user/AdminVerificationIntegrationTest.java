package com.inu.jeongbobada.domain.user;

import com.inu.jeongbobada.domain.user.entity.User;
import com.inu.jeongbobada.domain.user.entity.VerificationStatus;
import com.inu.jeongbobada.domain.user.repository.UserRepository;
import com.inu.jeongbobada.global.file.FileStorage;
import com.inu.jeongbobada.global.mail.EmailSender;
import com.inu.jeongbobada.global.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #142 2단계: 관리자가 가입 신청을 보고 승인·거절한다. 처리하면 서류(S3)를 지우고 결과 메일을 보낸다.
// 서류 삭제·메일은 커밋 뒤에 실행되므로 MockMvc 요청이 끝난 시점에 확인할 수 있다.
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminVerificationIntegrationTest {

    private static final String DOCUMENT_KEY = "verification/admin-test.pdf";
    private static final String DOCUMENT_URL = "https://storage.example.com/verification/admin-test.pdf?signed";

    @MockitoBean
    private FileStorage fileStorage;

    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String applicantStudentId;
    private String applicantEmail;
    private Long applicantId;
    private String adminStudentId;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        when(fileStorage.upload(anyString(), any(), any(), any())).thenReturn(DOCUMENT_KEY);
        when(fileStorage.presignedGetUrl(eq(DOCUMENT_KEY), eq(Duration.ofMinutes(5)))).thenReturn(DOCUMENT_URL);

        int n = ThreadLocalRandom.current().nextInt(100_000_000);
        applicantStudentId = String.format("9%08d", n);
        applicantEmail = "ap" + n + "@example.com";
        mockMvc.perform(SignupRequests.signup(applicantStudentId, "password1", "ap" + (n % 10_000), applicantEmail))
            .andExpect(status().isCreated());
        applicantId = userRepository.findByStudentId(applicantStudentId).orElseThrow().getUserId();

        adminStudentId = String.format("8%08d", n);
        mockMvc.perform(SignupRequests.signup(adminStudentId, "password1", "ad" + (n % 10_000), "ad" + n + "@example.com"))
            .andExpect(status().isCreated());
        jdbcTemplate.update("update users set role = 'ADMIN', verification_status = 'APPROVED' where student_id = ?", adminStudentId);
        adminToken = jwtTokenProvider.createAccessToken(adminStudentId);
    }

    @AfterEach
    void cleanUp() {
        userRepository.findByStudentId(applicantStudentId).ifPresent(userRepository::delete);
        userRepository.findByStudentId(adminStudentId).ifPresent(userRepository::delete);
    }

    @Test
    void 대기_목록과_상세에서_서류를_보고_승인하면_서류를_지우고_승인_메일을_보낸다() throws Exception {
        mockMvc.perform(get("/api/admin/verifications").param("status", "PENDING").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].userId").value(hasItem(applicantId.intValue())));

        mockMvc.perform(get("/api/admin/verifications/{id}", applicantId).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.applicant.studentId").value(applicantStudentId))
            .andExpect(jsonPath("$.data.applicant.documentType").value("pdf"))
            .andExpect(jsonPath("$.data.documentUrl").value(DOCUMENT_URL));

        mockMvc.perform(post("/api/admin/verifications/{id}/approve", applicantId).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        User applicant = userRepository.findById(applicantId).orElseThrow();
        assertThat(applicant.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(applicant.getVerificationDocumentKey()).isNull();
        assertThat(applicant.getVerificationProcessedAt()).isNotNull();
        verify(fileStorage).delete(DOCUMENT_KEY);
        verify(emailSender).send(eq(applicantEmail), eq("[정보바다] 가입이 승인되었어요"), anyString());

        mockMvc.perform(get("/api/admin/verifications").param("status", "APPROVED").header("Authorization", "Bearer " + adminToken))
            .andExpect(jsonPath("$.data[*].userId").value(hasItem(applicantId.intValue())));
    }

    @Test
    void 거절하면_사유를_저장하고_반려_메일에_사유를_넣는다() throws Exception {
        mockMvc.perform(post("/api/admin/verifications/{id}/reject", applicantId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  학번이 보이지 않아요  \"}"))
            .andExpect(status().isOk());

        User applicant = userRepository.findById(applicantId).orElseThrow();
        assertThat(applicant.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(applicant.getRejectionReason()).isEqualTo("학번이 보이지 않아요");
        assertThat(applicant.getVerificationDocumentKey()).isNull();
        verify(fileStorage).delete(DOCUMENT_KEY);
        verify(emailSender).send(eq(applicantEmail), eq("[정보바다] 가입 신청이 반려되었어요"), contains("반려 사유: 학번이 보이지 않아요"));

        mockMvc.perform(get("/api/admin/verifications").param("status", "REJECTED").header("Authorization", "Bearer " + adminToken))
            .andExpect(jsonPath("$.data[?(@.userId == " + applicantId + ")].rejectionReason").value(hasItem("학번이 보이지 않아요")));
    }

    @Test
    void 거절_사유가_비어_있으면_400이고_대기_상태를_유지한다() throws Exception {
        for (String body : new String[] {"{}", "{\"reason\":\"   \"}"}) {
            mockMvc.perform(post("/api/admin/verifications/{id}/reject", applicantId)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        }

        User applicant = userRepository.findById(applicantId).orElseThrow();
        assertThat(applicant.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(applicant.getVerificationDocumentKey()).isEqualTo(DOCUMENT_KEY);
        verify(fileStorage, never()).delete(anyString());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void 이미_처리된_신청은_409이고_서류_삭제와_메일을_다시_하지_않는다() throws Exception {
        mockMvc.perform(post("/api/admin/verifications/{id}/approve", applicantId).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/verifications/{id}/reject", applicantId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"학번이 보이지 않아요\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERIFICATION_ALREADY_PROCESSED"));

        verify(emailSender, never()).send(anyString(), eq("[정보바다] 가입 신청이 반려되었어요"), anyString());
        assertThat(userRepository.findById(applicantId).orElseThrow().getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
    }

    @Test
    void 없는_사용자는_404() throws Exception {
        mockMvc.perform(post("/api/admin/verifications/{id}/approve", Long.MAX_VALUE).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 관리자가_아니면_403_토큰이_없으면_401() throws Exception {
        String userToken = jwtTokenProvider.createAccessToken(applicantStudentId);

        mockMvc.perform(post("/api/admin/verifications/{id}/approve", applicantId).header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/verifications").param("status", "PENDING"))
            .andExpect(status().isUnauthorized());

        assertThat(userRepository.findById(applicantId).orElseThrow().getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
    }
}
