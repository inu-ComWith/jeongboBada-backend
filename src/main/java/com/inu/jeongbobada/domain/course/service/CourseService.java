package com.inu.jeongbobada.domain.course.service;
import com.inu.jeongbobada.domain.course.dto.response.CourseDetailResDto;
import com.inu.jeongbobada.domain.course.dto.response.CourseListResDto;
import com.inu.jeongbobada.domain.course.dto.response.CourseOfferingResDto;
import com.inu.jeongbobada.domain.course.dto.response.CoursePageResDto;
import com.inu.jeongbobada.domain.course.entity.Course;
import com.inu.jeongbobada.domain.course.entity.CourseOffering;
import com.inu.jeongbobada.domain.course.exception.CourseErrorCode;
import com.inu.jeongbobada.domain.course.repository.CourseOfferingRepository;
import com.inu.jeongbobada.domain.course.repository.CourseRepository;
import com.inu.jeongbobada.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor //생성자 생성
@Transactional(readOnly = true)
// 조회 기능 readOnly = true
public class CourseService {
    private final CourseRepository courseRepository;
    private final CourseOfferingRepository courseOfferingRepository;


    // 과목 리스트 전체 조회 (이번 학기 개설된 강의 기준)
    public CoursePageResDto getCourses(String keyword, int page, int size) {
        String normalizedKeyword = keyword.replaceAll("\\s+", "");
        PageRequest pageable = PageRequest.of(page, size);

        Page<CourseOffering> offeringPage;

        if (normalizedKeyword.isEmpty()) {
            offeringPage = courseOfferingRepository.findAllActive(pageable);
        } else if (isInitialConsonantKeyword(normalizedKeyword)) {
            String initialPattern = toInitialConsonantPattern(normalizedKeyword);
            offeringPage = courseOfferingRepository.searchActiveByInitialConsonant(
                initialPattern,
                pageable
            );
        } else {
            offeringPage = courseOfferingRepository.searchActiveByKeyword(
                normalizedKeyword,
                pageable
            );
        }

        List<CourseListResDto> courses = offeringPage.getContent()
            .stream()
            .map(CourseListResDto::from)
            .toList();

        return new CoursePageResDto(
            courses,
            offeringPage.getNumber(),
            offeringPage.getSize(),
            offeringPage.getTotalElements(),
            offeringPage.getTotalPages(),
            offeringPage.hasNext()
        );
    }

    private boolean isInitialConsonantKeyword(String keyword) {
        String initialConsonants = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";

        return keyword.chars()
            .allMatch(character -> initialConsonants.indexOf(character) >= 0);
    }

    private String toInitialConsonantPattern(String keyword) {
        StringBuilder pattern = new StringBuilder();

        for (char consonant : keyword.toCharArray()) {
            pattern.append(switch (consonant) {
                case 'ㄱ' -> "[가-깋]";
                case 'ㄲ' -> "[까-낗]";
                case 'ㄴ' -> "[나-닣]";
                case 'ㄷ' -> "[다-딯]";
                case 'ㄸ' -> "[따-띻]";
                case 'ㄹ' -> "[라-맇]";
                case 'ㅁ' -> "[마-밓]";
                case 'ㅂ' -> "[바-빟]";
                case 'ㅃ' -> "[빠-삫]";
                case 'ㅅ' -> "[사-싷]";
                case 'ㅆ' -> "[싸-앃]";
                case 'ㅇ' -> "[아-잏]";
                case 'ㅈ' -> "[자-짛]";
                case 'ㅉ' -> "[짜-찧]";
                case 'ㅊ' -> "[차-칳]";
                case 'ㅋ' -> "[카-킿]";
                case 'ㅌ' -> "[타-팋]";
                case 'ㅍ' -> "[파-핗]";
                case 'ㅎ' -> "[하-힣]";
                default -> throw new IllegalArgumentException("지원하지 않는 초성입니다.");
            });
        }

        return pattern.toString();
    }



    // 과목 상세 조회
    public CourseDetailResDto getCourse(Long courseId) {

        Course course = courseRepository.findById(courseId)
            .orElseThrow(()-> new BusinessException(CourseErrorCode.COURSE_NOT_FOUND));

        List<CourseOfferingResDto> offerings = courseOfferingRepository
            .findAllByCourse_CourseIdAndActiveTrue(courseId)
            .stream()
            .map(CourseOfferingResDto::from)
            .toList();

        return new CourseDetailResDto(
            course.getCourseId(),
            course.getCourseCode(),
            course.getCourseName(),
            course.getCourseDetail(),
            offerings
        );
    }
}
