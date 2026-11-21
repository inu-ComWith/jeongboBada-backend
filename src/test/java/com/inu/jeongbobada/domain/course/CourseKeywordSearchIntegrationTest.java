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
import com.inu.jeongbobada.domain.courseReview.entity.CourseReview;
import com.inu.jeongbobada.domain.courseReview.enums.AcademicYear;
import com.inu.jeongbobada.domain.courseReview.enums.Attendance;
import com.inu.jeongbobada.domain.courseReview.enums.ExamCount;
import com.inu.jeongbobada.domain.courseReview.enums.Frequency;
import com.inu.jeongbobada.domain.courseReview.enums.GradingType;
import com.inu.jeongbobada.domain.courseReview.enums.GroupActivity;
import com.inu.jeongbobada.domain.courseReview.enums.Rating;
import com.inu.jeongbobada.domain.courseReview.repository.CourseReviewRepository;
import com.inu.jeongbobada.domain.professor.entity.Professor;
import com.inu.jeongbobada.domain.professor.repository.ProfessorRepository;
import com.inu.jeongbobada.domain.user.entity.User;
import com.inu.jeongbobada.domain.user.repository.UserRepository;
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

import static org.hamcrest.Matchers.contains;
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

    @Autowired
    private CourseReviewRepository courseReviewRepository;

    @Autowired
    private UserRepository userRepository;

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
            .andExpect(jsonPath("$.data.courses[*].courseId", hasItem(course.getCourseId().intValue())));

        mockMvc.perform(get("/api/courses")
                .param("keyword", "검색교수" + professorSuffix))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.courses[*].courseId", hasItem(course.getCourseId().intValue())));
    }

    @Test
    void 검색어와_저장된_이름의_공백을_무시한다() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("keyword", "검 색 자 료 구 조 " + uniqueSuffix))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.courses[*].courseId", hasItem(course.getCourseId().intValue())));
    }

    @Test
    void 강의명과_교수명을_초성으로_검색한다() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("keyword", "ㄱㅅㅈㄹㄱㅈ"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.courses[*].courseId", hasItem(course.getCourseId().intValue())));

        mockMvc.perform(get("/api/courses")
                .param("keyword", "ㄱㅅㄱㅅ"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.courses[*].courseId", hasItem(course.getCourseId().intValue())));
    }

    @Test
    void 검색어를_생략하면_전체_활성_강의를_반환한다() throws Exception {
        mockMvc.perform(get("/api/courses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.courses[*].courseId", hasItem(course.getCourseId().intValue())))
            .andExpect(jsonPath("$.data.courses[*].courseId", not(hasItem(inactiveCourse.getCourseId().intValue()))));
    }

    @Test
    void 공백만_있는_검색어는_전체_활성_강의를_반환한다() throws Exception {
        mockMvc.perform(get("/api/courses").param("keyword", "   "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.courses[*].courseId", hasItem(course.getCourseId().intValue())))
            .andExpect(jsonPath("$.data.courses[*].courseId", not(hasItem(inactiveCourse.getCourseId().intValue()))));
    }

    @Test
    void 검색_결과가_없으면_빈_배열을_반환한다() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("keyword", "존재하지않는검색어" + uniqueSuffix))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.courses").isEmpty())
            .andExpect(jsonPath("$.data.totalElements").value(0))
            .andExpect(jsonPath("$.data.totalPages").value(0))
            .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 검색어의_퍼센트와_밑줄은_와일드카드가_아니라_글자로_찾는다() throws Exception {
        // 와일드카드로 해석되면 '%'는 "구조", '__'는 "구조" 두 글자와 맞아서 이 과목이 검색된다
        mockMvc.perform(get("/api/courses")
                .param("keyword", "자료%" + uniqueSuffix))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.courses[*].courseId", not(hasItem(course.getCourseId().intValue()))));

        mockMvc.perform(get("/api/courses")
                .param("keyword", "자료__" + uniqueSuffix))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.courses[*].courseId", not(hasItem(course.getCourseId().intValue()))));
    }

    @Test
    void 검색_결과에_페이지_정보와_전체_개수를_반환한다() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("keyword", "자료구조" + uniqueSuffix)
                .param("page", "0")
                .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.courses.length()").value(1))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(1))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 강의명을_가나다순으로_정렬한다() throws Exception {
        List<Course> sortedCourses = courseRepository.saveAll(List.of(
            new Course("가나다 정렬 " + uniqueSuffix, "SORT-A-" + uniqueSuffix, "가나다순 테스트"),
            new Course("데이터 정렬 " + uniqueSuffix, "SORT-D-" + uniqueSuffix, "가나다순 테스트"),
            new Course("사물 정렬 " + uniqueSuffix, "SORT-S-" + uniqueSuffix, "가나다순 테스트")
        ));
        List<CourseOffering> sortedOfferings = courseOfferingRepository.saveAll(
            sortedCourses.stream()
                .map(sortedCourse -> new CourseOffering(
                    sortedCourse, professor, 2026, Semester.FIRST, Grade.SECOND, Credits.THIRD,
                    "수 1,2", CourseType.MAJOR_CORE, EvaluationType.RELATIVE, IsOnline.OFFLINE
                ))
                .toList()
        );

        try {
            mockMvc.perform(get("/api/courses")
                    .param("keyword", "정렬" + uniqueSuffix)
                    .param("page", "0")
                    .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[*].courseName", contains(
                    "가나다 정렬 " + uniqueSuffix,
                    "데이터 정렬 " + uniqueSuffix,
                    "사물 정렬 " + uniqueSuffix
                )));
        } finally {
            courseOfferingRepository.deleteAll(sortedOfferings);
            courseRepository.deleteAll(sortedCourses);
        }
    }

    @Test
    void 평점순을_선택하면_평균_평점_내림차순으로_정렬한다() throws Exception {
        List<Course> courses = courseRepository.saveAll(List.of(
            new Course("가나다 평점정렬 " + uniqueSuffix, "RATING-A-" + uniqueSuffix, "평점순 테스트"),
            new Course("데이터 평점정렬 " + uniqueSuffix, "RATING-D-" + uniqueSuffix, "평점순 테스트"),
            new Course("사물 평점정렬 " + uniqueSuffix, "RATING-S-" + uniqueSuffix, "평점순 테스트")
        ));
        List<CourseOffering> offerings = courseOfferingRepository.saveAll(
            courses.stream()
                .map(sortedCourse -> new CourseOffering(
                    sortedCourse, professor, 2026, Semester.FIRST, Grade.SECOND, Credits.THIRD,
                    "목 1,2", CourseType.MAJOR_CORE, EvaluationType.RELATIVE, IsOnline.OFFLINE
                ))
                .toList()
        );
        User reviewer = userRepository.save(User.create(
            "7" + uniqueSuffix,
            "encoded-password",
            "정렬" + professorSuffix,
            "정보통신공학과",
            "sort" + uniqueSuffix + "@example.com",
            "test-document-key"
        ));
        User otherReviewer = userRepository.save(User.create(
            "6" + uniqueSuffix,
            "encoded-password",
            "정렬보" + professorSuffix,
            "정보통신공학과",
            "other-sort" + uniqueSuffix + "@example.com",
            "test-document-key"
        ));
        List<CourseReview> reviews = courseReviewRepository.saveAll(List.of(
            review(reviewer, courses.get(0), Rating.ONE),
            review(reviewer, courses.get(1), Rating.FIVE),
            review(reviewer, courses.get(2), Rating.THREE),
            review(otherReviewer, courses.get(0), Rating.FIVE),
            review(otherReviewer, courses.get(1), Rating.FIVE),
            review(otherReviewer, courses.get(2), Rating.THREE)
        ));

        try {
            mockMvc.perform(get("/api/courses")
                    .param("keyword", "평점정렬" + uniqueSuffix)
                    .param("sort", "rating")
                    .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[*].courseName", contains(
                    "데이터 평점정렬 " + uniqueSuffix,
                    "가나다 평점정렬 " + uniqueSuffix,
                    "사물 평점정렬 " + uniqueSuffix
                )));
        } finally {
            courseReviewRepository.deleteAll(reviews);
            courseOfferingRepository.deleteAll(offerings);
            courseRepository.deleteAll(courses);
            userRepository.deleteAll(List.of(reviewer, otherReviewer));
        }
    }

    @Test
    void 잘못된_정렬_기준이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/courses").param("sort", "unknown"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 페이지_번호나_크기가_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/courses").param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        mockMvc.perform(get("/api/courses").param("page", "2147483647"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        mockMvc.perform(get("/api/courses").param("size", "51"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 검색어가_50자를_초과하면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/courses")
                .param("keyword", "가".repeat(51)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
            .andExpect(jsonPath("$.errors[0].field").value("keyword"));
    }

    private CourseReview review(User reviewer, Course reviewCourse, Rating rating) {
        return new CourseReview(
            reviewer,
            reviewCourse,
            professor,
            AcademicYear.YEAR_2026,
            Semester.FIRST,
            rating,
            "평점 정렬 통합 테스트를 위한 충분히 긴 후기 내용입니다.",
            GroupActivity.SOMETIMES,
            Attendance.ELECTRONIC,
            ExamCount.TWO,
            Frequency.NORMAL,
            Frequency.LOW,
            GradingType.GENEROUS
        );
    }
}
