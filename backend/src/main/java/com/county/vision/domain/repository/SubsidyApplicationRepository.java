package com.county.vision.domain.repository;

import com.county.vision.domain.entity.SubsidyApplication;
import com.county.vision.domain.enums.SubsidyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubsidyApplicationRepository extends JpaRepository<SubsidyApplication, Long> {
    Optional<SubsidyApplication> findFirstByStudentIdOrderByIdDesc(Long studentId);
    List<SubsidyApplication> findByStatusOrderByIdDesc(SubsidyStatus status);
    List<SubsidyApplication> findByGlassesIdOrderByIdDesc(Long glassesId);
    boolean existsByGlassesIdAndStatus(Long glassesId, SubsidyStatus status);
    long countByStatus(SubsidyStatus status);
}
