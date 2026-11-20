package com.inu.jeongbobada.domain.professor.repository;

import com.inu.jeongbobada.domain.professor.entity.Professor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfessorRepository extends JpaRepository<Professor, Long> {
    Optional<Professor> findByProfessorId(Long professorId);
    // findByProfessorName -> 이름 전체 검색
    // findByProfessorNameContaining -> 이름 중에 한 자만이라도 검색
    // findByProfessorNameContainingIgnoreCase -> 대소문자 무시하고 검색
    List<Professor> findByProfessorNameContainingIgnoreCase(String professorName);
}
