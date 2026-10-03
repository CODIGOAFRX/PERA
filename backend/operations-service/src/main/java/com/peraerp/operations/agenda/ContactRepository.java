package com.peraerp.operations.agenda;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    Optional<Contact> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("select c from Contact c where c.companyId = :companyId " +
            "and (:filterActive = false or c.active = :active) " +
            "and (:filterCustomer = false or c.customerId = :customerId) " +
            "and (:filterQuery = false or lower(c.name) like lower(concat('%', :query, '%')) " +
            "or lower(c.organization) like lower(concat('%', :query, '%')) " +
            "or lower(c.phone) like lower(concat('%', :query, '%')) " +
            "or lower(c.mobile) like lower(concat('%', :query, '%')) " +
            "or lower(c.email) like lower(concat('%', :query, '%')) " +
            "or lower(c.city) like lower(concat('%', :query, '%')))")
    Page<Contact> search(@Param("companyId") UUID companyId,
                         @Param("filterActive") boolean filterActive, @Param("active") boolean active,
                         @Param("filterCustomer") boolean filterCustomer, @Param("customerId") UUID customerId,
                         @Param("filterQuery") boolean filterQuery, @Param("query") String query,
                         Pageable pageable);
}
