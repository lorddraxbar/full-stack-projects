package com.secphils.repository;

import com.secphils.entity.DropdownCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DropdownCategoryRepository extends JpaRepository<DropdownCategory, Long> {

    Optional<DropdownCategory> findByName(String name);
}
