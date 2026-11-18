package com.inu.jeongbobada.domain.material.exception;

import com.inu.jeongbobada.global.exception.code.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum MaterialErrorCode implements BaseErrorCode {

    MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "MATERIAL_NOT_FOUND", "학습자료를 찾을 수 없어요"), // 404
    // 작성자가 아닌 사람이 삭제하려 할 때
    MATERIAL_NOT_OWNER(HttpStatus.FORBIDDEN, "MATERIAL_NOT_OWNER", "내가 올린 자료만 삭제할 수 있어요"), // 403
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    MaterialErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
