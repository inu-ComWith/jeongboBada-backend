package com.inu.jeongbobada.domain.course.controller;
import com.inu.jeongbobada.domain.course.dto.response.CoursePageResDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.inu.jeongbobada.domain.course.dto.response.CourseDetailResDto;
import com.inu.jeongbobada.domain.course.service.CourseService;
import com.inu.jeongbobada.global.common.ApiResponse;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseController {


    private final CourseService courseService;


    // 과목 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<CoursePageResDto>> searchCourseList(
        @RequestParam(defaultValue = "")
        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
        String keyword,
        @RequestParam(defaultValue = "0")
        // 상한이 없으면 page×size가 int 범위를 넘어 Spring Data가 예외를 던지고 500이 된다
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        @Max(value = 10_000, message = "페이지 번호는 10000 이하여야 합니다.")
         int page,

        @RequestParam(defaultValue = "10")
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
         int size
    ) {
        return ResponseEntity.ok(
            ApiResponse.ok(courseService.getCourses(keyword, page, size))
        );
    }


    // 과목 상세 조회
    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseDetailResDto>> searchCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(
            ApiResponse.ok(courseService.getCourse(courseId))
        );
    }
}
