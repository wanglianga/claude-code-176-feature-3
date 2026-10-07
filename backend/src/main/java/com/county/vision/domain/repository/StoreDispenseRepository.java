package com.county.vision.domain.repository;

import com.county.vision.domain.entity.StoreDispense;
import com.county.vision.domain.enums.DispenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreDispenseRepository extends JpaRepository<StoreDispense, Long> {
    List<StoreDispense> findByStudentIdOrderByIdDesc(Long studentId);
    List<StoreDispense> findByGlassesIdOrderByIdDesc(Long glassesId);
    List<StoreDispense> findByStatusOrderByIdDesc(DispenseStatus status);
    List<StoreDispense> findByPaymentVoucherNo(String paymentVoucherNo);
    Optional<StoreDispense> findFirstByGlassesIdAndStatusOrderByIdDesc(Long glassesId, DispenseStatus status);
    long countByStatus(DispenseStatus status);
}
