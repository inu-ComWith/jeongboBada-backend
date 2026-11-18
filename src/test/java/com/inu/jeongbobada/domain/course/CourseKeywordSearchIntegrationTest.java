package com.inu.jeongbobada.domain.course;

import com.inu.jeongbobada.domain.course.entity.Course;
import com.inu.jeongbobada.domain.course.entity.CourseOffering;
import com.inu.jeongbobada.domain.course.enums.CourseType;
import com.inu.jeongbobada.domain.course.enums.Credits;
import com.inu.jeongbobada.domain.course.enums.EvaluationType;
import com.inu.jeongbobada.domain.course.enums.Grade;
import com.inu.jeongbobada.domain.course.enums.IsOnline;
import com.inu.jeongbobada.domain.course.enums.Semester;
import com.inu.jeongbobada.domain.course.repository.CourseOfferingRepository;
import com.inu.jeongbobada.domain.course.repository.CourseRepository;
import com.inu.jeongbobada.domain.professor.entity.Professor;
import com.inu.jeongbobada.domain.professor.repository.ProfessorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CourseKeywordSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ProfessorRepository professorRepository;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    private Course course;
    private Course inactiveCourse;
    private Professor professor;
    private Professor inactiveProfessor;
    private CourseOffering offering;
    private CourseOffering inactiveOffering;
    private String uniqueSuffix;
    private String professorSuffix;

    @BeforeEach
    void setUp() {
        int n = ThreadLocalRandom.current().nextInt(100_000_000);
        uniqueSuffix = String.valueOf(n);
        professorSuffix = String.valueOf(n % 100_000);

        course = courseRepository.save(new Course(
            "검색 자료 구조 " + uniqueSuffix,
            "SEARCH-" + uniqueSuffix,
            "키워드 검색 통합 테스트용 과목"
        ));
        professor = professorRepository.save(new Professor(
            null,
            "검색 교수 " + professorSuffix,
            "키워드 검색 통합 테스트용 교수",
            null,
            "200",
            "032-000-0000",
            "search" + uniqueSuffix + "@inu.ac.kr"
        ));
        offering = courseOfferingRepository.save(new CourseOffering(
            course, professor, 2026, Semester.FIRST, Grade.SECOND, Credits.THIRD,
            "월 1,2", CourseType.MAJOR_CORE, EvaluationType.RELATIVE, IsOnline.OFFLINE
        ));

        inactiveCourse = courseRepository.save(new Course(
            "비활성 검색 과목 " + uniqueSuffix,
            "INACTIVE-" + uniqueSuffix,
            "비활성 강의 검색 제외 테스트"
        ));
        inactiveProfessor = professorRepository.save(new Professor(
            null,
            "비활성 교수 " + professorSuffix,
            "비활성 강의 검색 제외 테스트용 교수",
            null,
            "201",
            "032-000-0001",
            "inactive" + uniqueSuffix + "@inu.ac.kr"
        ));
        inactiveOffering = new CourseOffering(
            inactiveCourse, inactiveProfessor, 2026, Semester.FIRST, Grade.SECOND, Credits.THIRD,
            "화 1,2", CourseType.MAJOR_CORE, EvaluationType.RELATIVE, IsOnline.OFFLINE
        );
        inactiveOffering.deactivate();
        inactiveOffering = courseOfferingRepository.save(inactiveOffering);
    }

    @AfterEach
    void cleanUp() {
        courseOfferingRepository.deleteAll(List.of(offering, inactiveOffering));
        courseRepository.deleteAll(List.of(course, inactiveCourse));
        professorRepository.deleteAll(List.of(professor, inactiveProfessor));
    }

    @Test
    void 강의명과_교수명을_부분_일치로_검색한다() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("keyword", "자료구조" + uniqueSuffix))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].courseId", hasItem(course.getCourseId().intValue())));

        mockMvc.perform(get("/api/courses")
                .param("keyword", "검색교수" + professorSuffix))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].courseId", hasItem(course.getCourseId().intValue())));
    }

    @Test
    void 검색어와_저장된_이름의_공백을_무시한다() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("keyword", "검 색 자 료 구 조 " + uniqueSuffix))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].courseId", hasItem(course.getCourseId().intValue())));
    }

    @Test
    void 검색어를_생략하면_전체_활성_강의를_반환한다() throws Exception {
        mockMvc.perform(get("/api/courses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].courseId", hasItem(course.getCourseId().intValue())))
            .andExpect(jsonPath("$.data[*].courseId", not(hasItem(inactiveCourse.getCourseId().intValue()))));
    }

    @Test
    void 공백만_있는_검색어는_전체_활성_강의를_반환한다() throws Exception {
        mockMvc.perform(get("/api/courses").param("keyword", "   "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].courseId", hasItem(course.getCourseId().intValue())))
            .andExpect(jsonPath("$.data[*].courseId", not(hasItem(inactiveCourse.getCourseId().intValue()))));
    }

    @Test
    void 검색_결과가_없으면_빈_배열을_반환한다() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("keyword", "존재하지않는검색어" + uniqueSuffix))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 검색어가_50자를_초과하면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("keyword", "가".repeat(51)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
            .andExpect(jsonPath("$.errors[0].field").value("keyword"));
    }
}
