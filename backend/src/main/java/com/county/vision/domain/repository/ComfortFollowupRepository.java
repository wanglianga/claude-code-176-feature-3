package com.county.vision.domain.repository;

import com.county.vision.domain.entity.ComfortFollowup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComfortFollowupRepository extends JpaRepository<ComfortFollowup, Long> {
    List<ComfortFollowup> findByStudentIdOrderByFeedbackDateDescIdDesc(Long studentId);
    List<ComfortFollowup> findByGlassesIdOrderByIdAsc(Long glassesId);
    List<ComfortFollowup> findByCallbackStatusOrderByCallbackDueDateAsc(String callbackStatus);
    List<ComfortFollowup> findAllByOrderByIdDesc();
}
