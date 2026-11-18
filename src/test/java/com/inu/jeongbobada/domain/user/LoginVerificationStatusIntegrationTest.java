package com.inu.jeongbobada.domain.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inu.jeongbobada.domain.user.repository.UserRepository;
import com.inu.jeongbobada.global.file.FileStorage;
import com.inu.jeongbobada.global.mail.EmailSender;
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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #142 4단계: 승인 대기·반려 계정은 토큰을 받지 못한다. 화면은 code로 팝업을 고르고 data로 신청일·반려 사유를 보여준다.
// 상태는 비밀번호 확인 뒤에 본다 (비밀번호를 모르면 계정 상태를 알 수 없어야 한다).
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class LoginVerificationStatusIntegrationTest {

    private static final String PASSWORD = "password1";

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String studentId;
    private String email;

    @BeforeEach
    void signUp() throws Exception {
        int n = ThreadLocalRandom.current().nextInt(100_000_000);
        studentId = String.format("9%08d", n);
        email = "vs" + n + "@example.com";
        mockMvc.perform(SignupRequests.signup(studentId, PASSWORD, "vs" + (n % 10_000), email))
            .andExpect(status().isCreated());
    }

    @AfterEach
    void cleanUp() {
        userRepository.findByStudentId(studentId).ifPresent(userRepository::delete);
    }

    @Test
    void 승인_대기면_403_VERIFICATION_PENDING과_신청일을_주고_토큰을_주지_않는다() throws Exception {
        login(PASSWORD)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("VERIFICATION_PENDING"))
            .andExpect(jsonPath("$.data.requestedAt").exists())
            .andExpect(jsonPath("$.data.rejectionReason").doesNotExist())
            .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void 반려면_403_VERIFICATION_REJECTED와_반려_사유를_준다() throws Exception {
        jdbcTemplate.update("""
            update users set verification_status = 'REJECTED', rejection_reason = '학번이 보이지 않아요'
            where student_id = ?
            """, studentId);

        login(PASSWORD)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("VERIFICATION_REJECTED"))
            .andExpect(jsonPath("$.data.rejectionReason").value("학번이 보이지 않아요"))
            .andExpect(jsonPath("$.data.requestedAt").doesNotExist());
    }

    @Test
    void 비밀번호가_틀리면_상태와_상관없이_401이라_상태가_드러나지_않는다() throws Exception {
        login("wrongPassword1")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 승인되면_로그인된다() throws Exception {
        SignupRequests.approve(jdbcTemplate, studentId);

        login(PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").exists());
    }

    // 로그인만 막으면, 차단 전에 받은 refresh token으로 계속 재발급받을 수 있다
    @Test
    void 승인_대기로_바뀐_계정은_토큰_재발급도_403() throws Exception {
        SignupRequests.approve(jdbcTemplate, studentId);
        String body = login(PASSWORD).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String refreshToken = objectMapper.readTree(body).path("data").path("refreshToken").asText();
        jdbcTemplate.update("update users set verification_status = 'PENDING' where student_id = ?", studentId);

        mockMvc.perform(post("/api/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("VERIFICATION_PENDING"));
    }

    // 승인 대기·반려 사용자도 비밀번호를 잊으면 재설정한 뒤 다시 제출할 수 있어야 한다
    @Test
    void 승인_대기여도_비밀번호_찾기는_막지_않는다() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"%s\",\"email\":\"%s\"}".formatted(studentId, email)))
            .andExpect(status().isOk());
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"studentId\":\"%s\",\"password\":\"%s\"}".formatted(studentId, password)));
    }
}
