package com.inu.jeongbobada.domain.material.dto.request;

import com.inu.jeongbobada.domain.material.enums.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

// 학습자료 업로드 (multipart/form-data). 검증 문구는 와이어프레임 10번·노션 기능명세서 기준
public record MaterialCreateRequest(
    @NotBlank(message = "제목을 입력해주세요")
    @Size(min = 5, max = 50, message = "제목은 5자 이상 50자 이하로 입력해주세요")
    String title,

    @NotBlank(message = "자료 설명을 입력해주세요")
    @Size(min = 10, max = 500, message = "자료 설명은 10자 이상 500자 이하로 입력해주세요")
    String content,

    @Schema(description = "자료 유형. 1개 이상, 같은 이름으로 여러 번 보낸다 (예: materialTypes=SUMMARY&materialTypes=PROBLEM_SOLVING)")
    @NotEmpty(message = "자료 유형을 선택해 주세요")
    Set<MaterialType> materialTypes,

    // 비어 있을 때 기본 400 대신 FILE_EMPTY로 안내하도록 FileStorage가 검사한다 (가입 서류와 같음)
    @Schema(description = "첨부 파일 1개 (pdf·jpg·png·pptx·docx·xlsx·hwp, 최대 20MB)", type = "string", format = "binary")
    MultipartFile file
) {
}
