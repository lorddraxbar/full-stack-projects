package com.secphils.repository;

import com.secphils.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    @Query("select c from Company c left join fetch c.authorizedRep")
    List<Company> findWithRep();
}
