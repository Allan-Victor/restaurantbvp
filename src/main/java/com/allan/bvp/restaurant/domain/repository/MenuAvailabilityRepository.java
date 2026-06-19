package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.MenuAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MenuAvailabilityRepository extends JpaRepository<MenuAvailability, UUID> {
}
