package com.inu.jeongbobada.domain.material;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inu.jeongbobada.domain.course.entity.Course;
import com.inu.jeongbobada.domain.course.repository.CourseRepository;
import com.inu.jeongbobada.domain.material.entity.Material;
import com.inu.jeongbobada.domain.material.enums.MaterialType;
import com.inu.jeongbobada.domain.material.repository.MaterialRepository;
import com.inu.jeongbobada.domain.user.SignupRequests;
import com.inu.jeongbobada.domain.user.repository.UserRepository;
import com.inu.jeongbobada.global.file.FileStorage;
import com.inu.jeongbobada.global.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #187 학습자료: 업로드(서류와 같은 FileStorage, material/) → 목록(내 글 맨 위) → 상세 → 내려받기 주소 → 삭제(작성자만).
// S3는 mock으로 바꾸고, 업로드 검증 자체는 FileStorageTest가 확인한다.
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MaterialIntegrationTest {

    private static final MockMultipartFile PDF =
        new MockMultipartFile("file", "중간고사 정리.pdf", "application/pdf", "%PDF-1.7 material".getBytes());

    @MockitoBean
    private FileStorage fileStorage;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger keySeq = new AtomicInteger();

    private Course course;
    private String writerId;
    private String otherId;
    private String writerToken;
    private String otherToken;

    @BeforeEach
    void setUp() throws Exception {
        when(fileStorage.upload(eq("material"), any(), any(), any()))
            .thenAnswer(invocation -> "material/key-" + keySeq.incrementAndGet() + ".pdf");
        int n = ThreadLocalRandom.current().nextInt(100_000_000);
        course = courseRepository.save(new Course("자료테스트과목", "M" + n, "학습자료 테스트용 과목"));
        writerId = signUpApproved("9%08d".formatted(n), "mw" + (n % 10_000), "mw" + n + "@example.com");
        otherId = signUpApproved("8%08d".formatted(n), "mo" + (n % 10_000), "mo" + n + "@example.com");
        writerToken = jwtTokenProvider.createAccessToken(writerId);
        otherToken = jwtTokenProvider.createAccessToken(otherId);
    }

    @AfterEach
    void cleanUp() {
        materialRepository.deleteAll(materialRepository.findAll().stream()
            .filter(m -> m.getCourse().getCourseId().equals(course.getCourseId())).toList());
        courseRepository.delete(course);
        userRepository.findByStudentId(writerId).ifPresent(userRepository::delete);
        userRepository.findByStudentId(otherId).ifPresent(userRepository::delete);
    }

    @Test
    void 자료를_올리면_201과_id를_주고_유형_여러_개와_파일_정보를_저장한다() throws Exception {
        long materialId = upload(writerToken, "중간고사 개념 정리");

        Material material = materialRepository.findById(materialId).orElseThrow();
        assertThat(material.getFileKey()).startsWith("material/");
        assertThat(material.getOriginalFileName()).isEqualTo("중간고사 정리.pdf");
        assertThat(material.getFileSize()).isEqualTo(PDF.getSize());
        mockMvc.perform(get("/api/materials/{id}", materialId).header("Authorization", "Bearer " + writerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.materialTypes").value(containsInAnyOrder("SUMMARY", "PROBLEM_SOLVING")))
            .andExpect(jsonPath("$.data.fileName").value("중간고사 정리.pdf"))
            .andExpect(jsonPath("$.data.fileType").value("pdf"))
            .andExpect(jsonPath("$.data.isMine").value(true));
    }

    @Test
    void 제목이_짧고_유형이_없으면_400이고_파일을_올리지_않는다() throws Exception {
        mockMvc.perform(multipart("/api/courses/{courseId}/materials", course.getCourseId())
                .file(PDF)
                .param("title", "짧음")
                .param("content", "설명은 열 글자를 넘게 적었습니다")
                .header("Authorization", "Bearer " + writerToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
            .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("title", "materialTypes")));

        verify(fileStorage, never()).upload(eq("material"), any(), any(), any());
    }

    @Test
    void 없는_과목에는_올릴_수_없다() throws Exception {
        mockMvc.perform(uploadRequest(Long.MAX_VALUE, "없는 과목 자료입니다", writerToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    @Test
    void 목록은_내_글이_맨_위이고_그다음_최신순이며_총_개수를_준다() throws Exception {
        long mine = upload(writerToken, "먼저 올린 내 자료");
        long others = upload(otherToken, "나중에 올린 남의 자료");

        mockMvc.perform(get("/api/courses/{courseId}/materials", course.getCourseId())
                .header("Authorization", "Bearer " + writerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.materials[0].materialId").value(mine))
            .andExpect(jsonPath("$.data.materials[0].isMine").value(true))
            .andExpect(jsonPath("$.data.materials[1].materialId").value(others))
            .andExpect(jsonPath("$.data.materials[1].fileType").value("pdf"));

        mockMvc.perform(get("/api/courses/{courseId}/materials", course.getCourseId())
                .param("size", "1")
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(jsonPath("$.data.materials[0].materialId").value(others))
            .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void 내려받기_주소는_원래_파일명으로_5분짜리를_만든다() throws Exception {
        long materialId = upload(writerToken, "내려받기 테스트 자료");
        when(fileStorage.presignedDownloadUrl(anyString(), eq(Duration.ofMinutes(5)), eq("중간고사 정리.pdf")))
            .thenReturn("https://storage.example.com/material/key.pdf?signed");

        mockMvc.perform(get("/api/materials/{id}/download", materialId).header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.downloadUrl").value("https://storage.example.com/material/key.pdf?signed"));
    }

    @Test
    void 삭제는_작성자만_하고_파일도_지운다() throws Exception {
        long materialId = upload(writerToken, "삭제 테스트 자료입니다");
        String fileKey = materialRepository.findById(materialId).orElseThrow().getFileKey();

        mockMvc.perform(delete("/api/materials/{id}", materialId).header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("MATERIAL_NOT_OWNER"));
        verify(fileStorage, never()).delete(anyString());

        mockMvc.perform(delete("/api/materials/{id}", materialId).header("Authorization", "Bearer " + writerToken))
            .andExpect(status().isOk());
        verify(fileStorage).delete(fileKey);
        mockMvc.perform(get("/api/materials/{id}", materialId).header("Authorization", "Bearer " + writerToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
    }

    @Test
    void 로그인하지_않으면_목록도_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/materials", course.getCourseId()))
            .andExpect(status().isUnauthorized());
    }

    private long upload(String token, String title) throws Exception {
        String body = mockMvc.perform(uploadRequest(course.getCourseId(), title, token))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("materialId").asLong();
    }

    private MockMultipartHttpServletRequestBuilder uploadRequest(Long courseId, String title, String token) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/courses/{courseId}/materials", courseId)
            .file(PDF)
            .param("title", title)
            .param("content", "시험 범위 개념과 기출 풀이를 정리했습니다")
            .param("materialTypes", MaterialType.SUMMARY.name(), MaterialType.PROBLEM_SOLVING.name())
            .header("Authorization", "Bearer " + token);
    }

    private String signUpApproved(String studentId, String nickname, String email) throws Exception {
        mockMvc.perform(SignupRequests.signup(studentId, "password1", nickname, email)).andExpect(status().isCreated());
        SignupRequests.approve(jdbcTemplate, studentId);
        return studentId;
    }
}
