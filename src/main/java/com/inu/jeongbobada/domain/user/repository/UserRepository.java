package com.inu.jeongbobada.domain.user.repository;

import com.inu.jeongbobada.domain.user.entity.User;
import com.inu.jeongbobada.domain.user.entity.VerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByStudentId(String studentId);

    // 로그인 실패 횟수를 동시 요청에서도 빠뜨리지 않으려고 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.studentId = :studentId")
    Optional<User> findByStudentIdForUpdate(@Param("studentId") String studentId);

    // 관리자 승인·거절이 동시에 들어와도 한 번만 처리되도록 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    Optional<User> findByNickname(String nickname);
    Optional<User> findByEmail(String email);

    // 승인 대기 목록: 먼저 가입한 순 (#142)
    List<User> findByVerificationStatusOrderByCreatedAtAsc(VerificationStatus status);

    // 승인·반려 목록: 최근 처리한 순. 검토 없이 승인 상태인 기존 계정(처리 시각 null)은 빼서 관리자가 처리한 건만 보여준다
    @Query("""
        select u from User u
        where u.verificationStatus = :status and u.verificationProcessedAt is not null
        order by u.verificationProcessedAt desc
        """)
    List<User> findProcessedByVerificationStatus(@Param("status") VerificationStatus status);

    boolean existsByStudentId(String studentId);
    boolean existsByNickname(String nickname);
}
