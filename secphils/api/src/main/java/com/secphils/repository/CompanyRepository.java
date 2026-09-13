package com.secphils.repository;

import com.secphils.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    /** Rep-pointer pre-flight for account erasure (companies RESTRICT stays:
      *  a rep handoff must be an explicit admin action, never a side effect). */
    List<Company> findByAuthorizedRepId(Long repUserId);

    @Query("select c from Company c left join fetch c.authorizedRep")
    List<Company> findWithRep();
}
