package com.startupvalidationbot.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.startupvalidationbot.entity.Decision;

public interface DecisionRepository extends JpaRepository<Decision, Long> {
    Optional<Decision> findByDealId(Long dealId);
}