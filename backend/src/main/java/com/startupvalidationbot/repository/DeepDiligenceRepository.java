package com.startupvalidationbot.repository;

import com.startupvalidationbot.entity.DeepDiligence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeepDiligenceRepository extends JpaRepository<DeepDiligence, Long> {
    Optional<DeepDiligence> findByDealId(Long dealId);
}