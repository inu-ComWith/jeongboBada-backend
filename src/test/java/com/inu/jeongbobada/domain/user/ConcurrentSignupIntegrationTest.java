package com.inu.jeongbobada.domain.user;

import com.inu.jeongbobada.domain.user.repository.UserRepository;
import com.inu.jeongbobada.global.file.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// #109: 가입은 "사전 중복 확인(findBy...) → 저장" 구조라, 확인과 저장 사이에 같은 학번 요청이 끼어들면
// DB UNIQUE 제약 위반이 올라온다(가입 버튼 더블클릭). 그 요청이 500이 아니라 409로 나가는지 확인한다.
// 실제 동시 요청이어야 재현되므로 MockMvc가 아니라 실제 포트로 띄운 서버에 병렬로 보낸다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ConcurrentSignupIntegrationTest {

    private static final int REQUESTS = 10;
    private static final String BOUNDARY = "jeongbobada-test-boundary";

    @MockitoBean
    private FileStorage fileStorage;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository userRepository;

    private String studentId;

    @AfterEach
    void cleanUp() {
        userRepository.findByStudentId(studentId).ifPresent(userRepository::delete);
    }

    @BeforeEach
    void stubUpload() {
        // 요청마다 다른 key를 돌려줘야 "어떤 서류를 지웠는지"가 의미 있다
        when(fileStorage.upload(anyString(), any(), any(), any()))
            .thenAnswer(invocation -> "verification/" + UUID.randomUUID() + ".pdf");
    }

    @Test
    void 같은_학번으로_동시에_가입하면_1건만_성공하고_나머지는_409() throws Exception {
        int n = ThreadLocalRandom.current().nextInt(100_000_000);
        studentId = String.format("9%08d", n);

        HttpClient client = HttpClient.newHttpClient();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < REQUESTS; i++) {
            // 학번만 같고 닉네임·이메일은 달라서, 겹치는 건 학번 UNIQUE 하나뿐이다
            String body = multipartBody(Map.of(
                "studentId", studentId,
                "password", "password1",
                "nickname", "c%03d%02d".formatted(n % 1_000, i),
                "email", "cs%d_%d@example.com".formatted(n, i)));
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/signup"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            results.add(pool.submit(() -> {
                start.await(); // 모든 스레드가 준비된 뒤 동시에 출발
                return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            }));
        }
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s != 201).allMatch(s -> s == 409);

        // 서류를 올린 뒤 저장에서 충돌한 요청은 올린 서류를 지운다. 사전 확인에서 걸린 요청은 올리지도 않는다
        long uploads = Mockito.mockingDetails(fileStorage).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("upload"))
            .count();
        verify(fileStorage, times((int) uploads - 1)).delete(anyString());
    }

    // 서류 내용은 검사하지 않는다(FileStorage가 mock). 가입 요청 형식만 맞춘다
    private static String multipartBody(Map<String, String> fields) {
        StringBuilder body = new StringBuilder();
        fields.forEach((name, value) -> body
            .append("--").append(BOUNDARY).append("\r\n")
            .append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
            .append(value).append("\r\n"));
        body.append("--").append(BOUNDARY).append("\r\n")
            .append("Content-Disposition: form-data; name=\"file\"; filename=\"proof.pdf\"\r\n")
            .append("Content-Type: application/pdf\r\n\r\n")
            .append("%PDF-1.7 test\r\n")
            .append("--").append(BOUNDARY).append("--\r\n");
        return body.toString();
    }
}
