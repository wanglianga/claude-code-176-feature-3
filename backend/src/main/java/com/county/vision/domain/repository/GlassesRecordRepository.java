package com.county.vision.domain.repository;

import com.county.vision.domain.entity.GlassesRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GlassesRecordRepository extends JpaRepository<GlassesRecord, Long> {
    List<GlassesRecord> findByStudentIdOrderByGlassesDateAscIdAsc(Long studentId);
    long countByPartnerOpticianFalse();
    long countByDiscomfortTrueAndDiscomfortResolvedFalse();

    /** 付款凭证号查重（同一凭证不能在两副眼镜/两张核验单上使用） */
    Optional<GlassesRecord> findFirstByPaymentVoucherNoAndIdNot(String paymentVoucherNo, Long id);
}
