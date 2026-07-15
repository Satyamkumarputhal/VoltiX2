package com.voltix.workflow.complaints;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PublicComplaintRepository extends JpaRepository<PublicComplaint, Long> {

    @Query("""
           SELECT c FROM PublicComplaint c
            WHERE c.addressHash = :addressHash
              AND c.status = :status
              AND c.submittedAt >= :since
           """)
    Optional<PublicComplaint> findFirstByAddressHashAndStatusAndSubmittedAtAfter(
            String addressHash,
            ComplaintStatus status,
            ZonedDateTime since
    );

    /**
     * Used by the operator triage panel to list complaints by status,
     * newest first. Spring Data JPA derives this query from the method name.
     */
    List<PublicComplaint> findByStatusOrderBySubmittedAtDesc(ComplaintStatus status);
}

