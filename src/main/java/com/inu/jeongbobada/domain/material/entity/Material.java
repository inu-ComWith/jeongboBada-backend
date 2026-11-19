package com.inu.jeongbobada.domain.material.entity;

import com.inu.jeongbobada.domain.course.entity.Course;
import com.inu.jeongbobada.domain.material.enums.MaterialType;
import com.inu.jeongbobada.domain.user.entity.User;
import com.inu.jeongbobada.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

// 강의별 학습자료 (와이어프레임 10번). 첨부 파일 1개가 필수다.
// 테이블 이름이 MATERIAL이 아닌 이유: 예전 MATERIAL 테이블에 "사용자·과목당 1개" UNIQUE와 단일 유형 NOT NULL 컬럼이 남아 있어서,
// ddl-auto: update로는 지워지지 않는다 (#187). 새 테이블로 시작하고 예전 테이블은 비어 있어 그대로 둔다
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "COURSE_MATERIAL")
public class Material extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MATERIAL_ID")
    private Long materialId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COURSE_ID", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Column(name = "TITLE", length = 50, nullable = false)
    private String title;

    @Column(name = "CONTENT", length = 500, nullable = false)
    private String content;

    // 여러 개 고를 수 있다 (예: 개념 정리 + 문제 풀이)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "COURSE_MATERIAL_TYPE", joinColumns = @JoinColumn(name = "MATERIAL_ID"))
    @Enumerated(EnumType.STRING)
    @Column(name = "MATERIAL_TYPE", length = 20, nullable = false)
    private Set<MaterialType> materialTypes = EnumSet.noneOf(MaterialType.class);

    // 첨부 파일의 S3 key (URL 아님. 비공개 저장소라 내려받을 때마다 짧게 만료되는 주소를 만든다)
    @Column(name = "FILE_KEY", length = 100, nullable = false)
    private String fileKey;

    // 사용자가 올린 원래 파일명. 내려받을 때 이 이름으로 저장되고, 상세 화면에 보여준다 (key에는 쓰지 않는다)
    @Column(name = "ORIGINAL_FILE_NAME", length = 255, nullable = false)
    private String originalFileName;

    @Column(name = "FILE_SIZE", nullable = false)
    private long fileSize;

    public Material(Course course, User user, String title, String content, Set<MaterialType> materialTypes,
                    String fileKey, String originalFileName, long fileSize) {
        this.course = course;
        this.user = user;
        this.title = title;
        this.content = content;
        this.materialTypes = EnumSet.copyOf(materialTypes);
        this.fileKey = fileKey;
        this.originalFileName = originalFileName;
        this.fileSize = fileSize;
    }

    public boolean isWrittenBy(Long userId) {
        return user.getUserId().equals(userId);
    }
}
