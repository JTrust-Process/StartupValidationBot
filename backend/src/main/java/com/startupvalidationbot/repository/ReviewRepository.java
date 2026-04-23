package com.startupvalidationbot.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.startupvalidationbot.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByDealId(Long dealId);
}