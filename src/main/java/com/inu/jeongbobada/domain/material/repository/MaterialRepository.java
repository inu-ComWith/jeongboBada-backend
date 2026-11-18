package com.inu.jeongbobada.domain.material.repository;

import com.inu.jeongbobada.domain.material.entity.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    // 내 글을 맨 위에 두고, 그다음 최신순 (와이어프레임 10번). 작성자 닉네임을 같이 읽어 N+1을 막는다
    @Query(value = """
        select m from Material m join fetch m.user
        where m.course.courseId = :courseId
        order by case when m.user.userId = :viewerId then 0 else 1 end, m.createdAt desc, m.materialId desc
        """,
        countQuery = "select count(m) from Material m where m.course.courseId = :courseId")
    Page<Material> findPageForViewer(@Param("courseId") Long courseId, @Param("viewerId") Long viewerId, Pageable pageable);

    @Query("select m from Material m join fetch m.user where m.materialId = :materialId")
    Optional<Material> findWithUserById(@Param("materialId") Long materialId);
}
