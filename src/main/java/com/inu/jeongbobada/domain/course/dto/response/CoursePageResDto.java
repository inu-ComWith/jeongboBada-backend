package com.inu.jeongbobada.domain.course.dto.response;

import java.util.List;

public record CoursePageResDto(
    List<CourseListResDto> courses,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
}
