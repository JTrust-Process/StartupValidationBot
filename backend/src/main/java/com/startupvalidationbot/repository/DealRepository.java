package com.startupvalidationbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.startupvalidationbot.entity.Deal;

public interface DealRepository extends JpaRepository<Deal, Long> {
}