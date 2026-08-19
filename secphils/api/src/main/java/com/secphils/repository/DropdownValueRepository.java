package com.secphils.repository;

import com.secphils.entity.DropdownValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DropdownValueRepository extends JpaRepository<DropdownValue, Long> {

    List<DropdownValue> findByCategoryIdOrderBySortOrderAsc(Long categoryId);
}
