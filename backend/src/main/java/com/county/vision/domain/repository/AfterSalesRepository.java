package com.county.vision.domain.repository;

import com.county.vision.domain.entity.AfterSales;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AfterSalesRepository extends JpaRepository<AfterSales, Long> {
    List<AfterSales> findByStudentIdOrderByIdDesc(Long studentId);
    List<AfterSales> findByStatusOrderByIdDesc(String status);
    List<AfterSales> findAllByOrderByIdDesc();
    List<AfterSales> findByGlassesIdOrderByIdDesc(Long glassesId);
}
