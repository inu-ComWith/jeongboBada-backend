package com.inu.jeongbobada.domain.user;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

// 가입은 multipart(가입 정보 + 재학 증빙 서류)라 요청 만들기가 길어서, 가입이 필요한 통합 테스트가 같이 쓴다.
// 서류 업로드(FileStorage)는 각 테스트에서 @MockitoBean으로 바꿔서 S3를 부르지 않는다.
public final class SignupRequests {

    public static final MockMultipartFile FAKE_PDF =
        new MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-1.7 test".getBytes());

    private SignupRequests() {
    }

    public static MockMultipartHttpServletRequestBuilder signup(String studentId, String password, String nickname, String email) {
        return multipart("/api/auth/signup")
            .file(FAKE_PDF)
            .param("studentId", studentId)
            .param("password", password)
            .param("nickname", nickname)
            .param("email", email);
    }
}
