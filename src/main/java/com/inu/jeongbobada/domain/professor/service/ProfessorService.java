package com.inu.jeongbobada.domain.professor.service;

import com.inu.jeongbobada.domain.professor.dto.ProfessorDetailResponseDto;
import com.inu.jeongbobada.domain.professor.dto.ProfessorListResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.inu.jeongbobada.domain.professor.entity.Professor;
import com.inu.jeongbobada.domain.professor.exception.ProfessorErrorCode;
import com.inu.jeongbobada.domain.professor.repository.ProfessorRepository;
import com.inu.jeongbobada.global.exception.BusinessException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfessorService {
    private final ProfessorRepository professorRepository;

    public List<ProfessorListResponseDto> getProfessorLists() {
        List<Professor> professorLists = professorRepository.findAll();

        return professorLists.stream().map(ProfessorListResponseDto::from).toList();
    }

    public ProfessorDetailResponseDto getProfessorDetail(Long professorId) {
        Professor professorDetail = professorRepository.findByProfessorId(professorId)
            .orElseThrow(() -> new BusinessException(ProfessorErrorCode.PROFESSOR_NOT_FOUND));

        return ProfessorDetailResponseDto.from(professorDetail);
    }

    public List<ProfessorListResponseDto> searchProfessorLists(String professorName) {
        if (professorName == null) {
            return getProfessorLists(); // null이면 전체 List 반환
        }

        // .trim() -> 문자열 공백 제거
        List<Professor> professorSearchLists = professorRepository.findByProfessorNameContainingIgnoreCase(professorName.trim());

        return professorSearchLists.stream().map(ProfessorListResponseDto::from).toList();
    }
}
