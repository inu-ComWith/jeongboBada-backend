package com.inu.jeongbobada.domain.course.controller;

import com.inu.jeongbobada.domain.course.dto.response.CourseDetailResDto;
import com.inu.jeongbobada.domain.course.dto.response.CourseListResDto;
import com.inu.jeongbobada.domain.course.service.CourseService;
import com.inu.jeongbobada.global.common.ApiResponse;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseController {


    private final CourseService courseService;


    // 과목 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseListResDto>>> searchCourseList(
        @RequestParam(defaultValue = "")
        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
        String keyword
    ) {
        return ResponseEntity.ok(
            ApiResponse.ok(courseService.getCourses(keyword))
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
