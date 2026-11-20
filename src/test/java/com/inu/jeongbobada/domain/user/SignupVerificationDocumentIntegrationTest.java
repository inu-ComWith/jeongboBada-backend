package com.inu.jeongbobada.domain.user;

import com.inu.jeongbobada.domain.user.entity.User;
import com.inu.jeongbobada.domain.user.entity.VerificationStatus;
import com.inu.jeongbobada.domain.user.repository.UserRepository;
import com.inu.jeongbobada.global.file.FileStorage;
import com.inu.jeongbobada.global.file.FileType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.unit.DataSize;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #142 1단계: 가입할 때 재학 증빙 서류를 받아 S3에 올리고, 계정은 승인 대기(PENDING)로 저장한다.
// FileStorage는 spy라 검증(빈 파일·형식)은 실제로 돌고, S3 호출(upload 성공)만 바꿔 끼운다.
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SignupVerificationDocumentIntegrationTest {

    private static final String DOCUMENT_KEY = "verification/test-document.pdf";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private FileStorage fileStorage;

    private String studentId;
    private int n;

    @BeforeEach
    void setUp() {
        n = ThreadLocalRandom.current().nextInt(100_000_000);
        studentId = String.format("9%08d", n);
    }

    @AfterEach
    void cleanUp() {
        userRepository.findByStudentId(studentId).ifPresent(userRepository::delete);
    }

    @Test
    void 서류와_함께_가입하면_서류_key를_저장하고_승인_대기가_된다() throws Exception {
        doReturn(DOCUMENT_KEY).when(fileStorage).upload(any(), any(), any(), any());

        mockMvc.perform(SignupRequests.signup(studentId, "password1", "vd" + (n % 10_000), "vd" + n + "@example.com"))
            .andExpect(status().isCreated());

        verify(fileStorage).upload(eq("verification"), any(), eq(Set.of(FileType.PDF, FileType.JPG)), eq(DataSize.ofMegabytes(10)));
        User user = userRepository.findByStudentId(studentId).orElseThrow();
        assertThat(user.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(user.getVerificationDocumentKey()).isEqualTo(DOCUMENT_KEY);
    }

    // 닉네임 최대 10자: 검증뿐 아니라 DB 컬럼(length = 10)까지 저장되는지 확인한다
    @Test
    void 닉네임_10자로_가입할_수_있다() throws Exception {
        doReturn(DOCUMENT_KEY).when(fileStorage).upload(any(), any(), any(), any());
        String nickname = String.format("열글자닉네임%04d", n % 10_000);

        mockMvc.perform(SignupRequests.signup(studentId, "password1", nickname, "vd" + n + "@example.com"))
            .andExpect(status().isCreated());

        assertThat(userRepository.findByStudentId(studentId).orElseThrow().getNickname()).isEqualTo(nickname);
    }

    @Test
    void 서류_없이_가입하면_400_FILE_EMPTY이고_계정을_만들지_않는다() throws Exception {
        mockMvc.perform(multipart("/api/auth/signup")
                .param("studentId", studentId)
                .param("password", "password1")
                .param("nickname", "vd" + (n % 10_000))
                .param("email", "vd" + n + "@example.com"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FILE_EMPTY"));

        assertThat(userRepository.findByStudentId(studentId)).isEmpty();
    }

    @Test
    void 허용하지_않는_형식이면_400_FILE_TYPE_NOT_ALLOWED이고_계정을_만들지_않는다() throws Exception {
        MockMultipartFile png = new MockMultipartFile("file", "proof.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        mockMvc.perform(multipart("/api/auth/signup")
                .file(png)
                .param("studentId", studentId)
                .param("password", "password1")
                .param("nickname", "vd" + (n % 10_000))
                .param("email", "vd" + n + "@example.com"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"))
            .andExpect(jsonPath("$.message").value("pdf, jpg 파일만 올릴 수 있어요"));

        assertThat(userRepository.findByStudentId(studentId)).isEmpty();
    }

    // 이 기능 전에 가입한 계정(인증 상태 컬럼이 없던 행)은 승인 상태여야 로그인할 수 있다.
    // 컬럼 기본값이 그 역할을 하므로, 상태를 넣지 않고 INSERT해서 확인한다
    @Test
    void 인증_상태_없이_들어간_기존_계정은_승인_상태다() {
        jdbcTemplate.update("""
            INSERT INTO users (student_id, password, nickname, role, created_at, updated_at)
            VALUES (?, 'x', ?, 'USER', NOW(), NOW())
            """, studentId, "vd" + (n % 10_000));

        User user = userRepository.findByStudentId(studentId).orElseThrow();
        assertThat(user.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(user.getVerificationDocumentKey()).isNull();
        // 신청 시각 컬럼이 없던 계정은 가입일을 신청일로 쓴다
        assertThat(user.getVerificationRequestedAt()).isEqualTo(user.getCreatedAt());
    }
}
