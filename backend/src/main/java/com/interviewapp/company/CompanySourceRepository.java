package com.interviewapp.company;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanySourceRepository extends JpaRepository<CompanySource, UUID> {

    List<CompanySource> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
