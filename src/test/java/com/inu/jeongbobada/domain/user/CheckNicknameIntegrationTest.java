package com.inu.jeongbobada.domain.user;

import com.inu.jeongbobada.domain.user.repository.UserRepository;
import com.inu.jeongbobada.global.file.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #97: 가입 폼 제출 전 닉네임 사용 가능 여부를 미리 확인하는 API. 실제 DB에 저장된 닉네임과 겹치는지가
// 핵심이라 (findByNickname 등 서비스 mock으로는 의미가 없음) 통합 테스트로 검증한다.
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CheckNicknameIntegrationTest {

    // 가입 때 재학 증빙 서류를 S3에 올리는 부분. 이 테스트는 서류와 무관해서 S3를 부르지 않게 바꾼다
    @MockitoBean
    private FileStorage fileStorage;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private String createdStudentId;

    @AfterEach
    void cleanUp() {
        if (createdStudentId != null) {
            userRepository.findByStudentId(createdStudentId).ifPresent(userRepository::delete);
        }
    }

    private String signupWithNickname(String nickname) throws Exception {
        int n = ThreadLocalRandom.current().nextInt(100_000_000);
        String studentId = String.format("9%08d", n);
        createdStudentId = studentId;

        mockMvc.perform(SignupRequests.signup(studentId, "password1", nickname, "it" + n + "@example.com"))
            .andExpect(status().isCreated());
        return nickname;
    }

    @Test
    void 아무도_안_쓰는_닉네임은_사용_가능하다() throws Exception {
        String unusedNickname = "free" + ThreadLocalRandom.current().nextInt(100_000_000);

        mockMvc.perform(get("/api/auth/check-nickname").param("nickname", unusedNickname))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void 이미_가입된_닉네임은_사용_불가능하다() throws Exception {
        String nickname = signupWithNickname("t" + ThreadLocalRandom.current().nextInt(100_000));

        mockMvc.perform(get("/api/auth/check-nickname").param("nickname", nickname))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.available").value(false));
    }
}
