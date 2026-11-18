package com.inu.jeongbobada.domain.course.repository;

import com.inu.jeongbobada.domain.course.entity.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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

    // 같은 과목·교수 조합은 학기마다 한 건씩 개설될 수 있어서 한 건만 꺼내면 두 학기째부터 500이 난다 (#161).
    // 후기 작성은 개설한 적이 있는지만 필요하므로 존재 여부로 묻는다
    boolean existsByCourse_CourseIdAndProfessor_ProfessorId(Long courseId, Long professorId);
}
