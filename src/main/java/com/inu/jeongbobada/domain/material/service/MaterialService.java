package com.inu.jeongbobada.domain.material.service;

import com.inu.jeongbobada.domain.course.entity.Course;
import com.inu.jeongbobada.domain.course.exception.CourseErrorCode;
import com.inu.jeongbobada.domain.course.repository.CourseRepository;
import com.inu.jeongbobada.domain.material.dto.request.MaterialCreateRequest;
import com.inu.jeongbobada.domain.material.dto.response.MaterialDetailResponse;
import com.inu.jeongbobada.domain.material.dto.response.MaterialDownloadResponse;
import com.inu.jeongbobada.domain.material.dto.response.MaterialPageResponse;
import com.inu.jeongbobada.domain.material.dto.response.MaterialSummaryResponse;
import com.inu.jeongbobada.domain.material.entity.Material;
import com.inu.jeongbobada.domain.material.exception.MaterialErrorCode;
import com.inu.jeongbobada.domain.material.repository.MaterialRepository;
import com.inu.jeongbobada.domain.user.entity.User;
import com.inu.jeongbobada.domain.user.exception.UserErrorCode;
import com.inu.jeongbobada.domain.user.repository.UserRepository;
import com.inu.jeongbobada.global.exception.BusinessException;
import com.inu.jeongbobada.global.file.FileErrorCode;
import com.inu.jeongbobada.global.file.FileStorage;
import com.inu.jeongbobada.global.file.FileType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;

import java.time.Duration;
import java.util.Set;

// 강의별 학습자료 (#187). 첨부 파일은 S3 material/에 두고 DB에는 key만 저장한다
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialService {

    private static final String MATERIAL_PREFIX = "material";
    private static final Set<FileType> MATERIAL_FILE_TYPES =
        Set.of(FileType.PDF, FileType.JPG, FileType.PNG, FileType.PPTX, FileType.DOCX, FileType.XLSX, FileType.HWP);
    private static final DataSize MATERIAL_MAX_SIZE = DataSize.ofMegabytes(20);
    // 받자마자 여는 주소라 짧게 둔다. 만료되면 download API를 다시 부르면 된다
    private static final Duration DOWNLOAD_URL_EXPIRES_IN = Duration.ofMinutes(5);
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final MaterialRepository materialRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;

    // 검증에 걸리면 S3를 부르지 않는다. 저장이 실패하면 올린 파일을 지운다 (S3는 DB 트랜잭션에 묶이지 않음)
    @Transactional
    public Long create(Long userId, Long courseId, MaterialCreateRequest request) {
        Course course = findCourse(courseId);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        MultipartFile file = request.file();
        String fileKey = fileStorage.upload(MATERIAL_PREFIX, file, MATERIAL_FILE_TYPES, MATERIAL_MAX_SIZE);
        Material material = new Material(course, user, request.title().strip(), request.content().strip(),
            request.materialTypes(), fileKey, originalFileName(file, fileKey), file.getSize());
        try {
            return materialRepository.saveAndFlush(material).getMaterialId();
        } catch (RuntimeException e) {
            deleteQuietly(fileKey, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public MaterialPageResponse getPage(Long viewerId, Long courseId, int page, int size) {
        findCourse(courseId);
        Page<Material> materials = materialRepository.findPageForViewer(courseId, viewerId, PageRequest.of(page, size));
        return new MaterialPageResponse(
            materials.getTotalElements(),
            materials.hasNext(),
            materials.map(material -> MaterialSummaryResponse.of(material, viewerId)).getContent()
        );
    }

    @Transactional(readOnly = true)
    public MaterialDetailResponse getDetail(Long viewerId, Long materialId) {
        return MaterialDetailResponse.of(findMaterial(materialId), viewerId);
    }

    @Transactional(readOnly = true)
    public MaterialDownloadResponse getDownloadUrl(Long materialId) {
        Material material = findMaterial(materialId);
        try {
            return new MaterialDownloadResponse(fileStorage.presignedDownloadUrl(
                material.getFileKey(), DOWNLOAD_URL_EXPIRES_IN, material.getOriginalFileName()));
        } catch (SdkException e) {
            log.error("학습자료 다운로드 주소 생성 실패: key={}", material.getFileKey(), e);
            throw new BusinessException(FileErrorCode.FILE_URL_FAILED);
        }
    }

    // 파일 삭제는 커밋 뒤에 한다. 커밋 전에 지우면 롤백됐을 때 파일만 사라진다
    @Transactional
    public void delete(Long userId, Long materialId) {
        Material material = findMaterial(materialId);
        if (!material.isWrittenBy(userId)) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_NOT_OWNER);
        }
        String fileKey = material.getFileKey();
        materialRepository.delete(material);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(fileKey, null);
            }
        });
    }

    private Course findCourse(Long courseId) {
        return courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException(CourseErrorCode.COURSE_NOT_FOUND));
    }

    private Material findMaterial(Long materialId) {
        return materialRepository.findWithUserById(materialId)
            .orElseThrow(() -> new BusinessException(MaterialErrorCode.MATERIAL_NOT_FOUND));
    }

    // 원래 예외를 살려야 하므로 삭제 실패는 로그만 남긴다 (남은 파일은 로그의 key를 보고 지운다)
    private void deleteQuietly(String fileKey, RuntimeException cause) {
        try {
            fileStorage.delete(fileKey);
        } catch (RuntimeException deleteFailure) {
            if (cause != null) {
                cause.addSuppressed(deleteFailure);
            }
            log.error("학습자료 파일 삭제 실패: key={}", fileKey, deleteFailure);
        }
    }

    // 일부 브라우저는 경로까지 붙여 보내서 마지막 이름만 쓴다. 이름이 없으면 key의 확장자로 만든다
    private static String originalFileName(MultipartFile file, String fileKey) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "material." + FileType.extensionOfKey(fileKey);
        }
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).strip();
        return name.length() > MAX_FILE_NAME_LENGTH ? name.substring(name.length() - MAX_FILE_NAME_LENGTH) : name;
    }
}
