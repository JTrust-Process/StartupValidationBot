package com.startupvalidationbot.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.startupvalidationbot.entity.QuickScreen;

public interface QuickScreenRepository extends JpaRepository<QuickScreen, Long> {
    Optional<QuickScreen> findByDealId(Long dealId);
}