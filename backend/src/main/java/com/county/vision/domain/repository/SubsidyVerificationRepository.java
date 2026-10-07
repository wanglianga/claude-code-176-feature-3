package com.county.vision.domain.repository;

import com.county.vision.domain.entity.SubsidyVerification;
import com.county.vision.domain.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubsidyVerificationRepository extends JpaRepository<SubsidyVerification, Long> {
    Optional<SubsidyVerification> findByGlassesId(Long glassesId);
    List<SubsidyVerification> findByStudentIdOrderByIdDesc(Long studentId);
    List<SubsidyVerification> findByStatusOrderByIdDesc(VerificationStatus status);
    List<SubsidyVerification> findAllByOrderByIdDesc();
    long countByStatus(VerificationStatus status);
    boolean existsByStudentIdAndStatus(Long studentId, VerificationStatus status);
}
