package com.inu.jeongbobada.domain.course.repository;

import com.inu.jeongbobada.domain.course.entity.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    List<CourseOffering> findAllByActiveTrue();

    @Query("""
        select offering
        from CourseOffering offering
        join fetch offering.course course
        join fetch offering.professor professor
        where offering.active = true
          and (
            replace(course.courseName, ' ', '') like concat('%', :keyword, '%')
            or replace(professor.professorName, ' ', '') like concat('%', :keyword, '%')
          )
        """)
    List<CourseOffering> searchActiveByKeyword(@Param("keyword") String keyword);

    List<CourseOffering> findAllByCourse_CourseIdAndActiveTrue(Long courseId);

    Optional<CourseOffering> findByCourse_CourseIdAndProfessor_ProfessorId(Long courseId, Long professorId);
}
