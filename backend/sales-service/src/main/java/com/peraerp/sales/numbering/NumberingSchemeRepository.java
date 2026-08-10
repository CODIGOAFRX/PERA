package com.peraerp.sales.numbering;

import com.peraerp.sales.document.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NumberingSchemeRepository extends JpaRepository<NumberingScheme, UUID> {

    Optional<NumberingScheme> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<NumberingScheme> findByCompanyIdAndDocumentTypeAndDefaultSchemeTrueAndActiveTrue(
            UUID companyId, DocumentType documentType);

    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select n from NumberingScheme n where n.companyId = :companyId " +
            "and (:type is null or n.documentType = :type) " +
            "and (:active is null or n.active = :active) " +
            "and (:query = '' or lower(n.code) like lower(concat('%', :query, '%')) " +
            "or lower(n.name) like lower(concat('%', :query, '%')))")
    Page<NumberingScheme> search(@Param("companyId") UUID companyId,
                                 @Param("type") DocumentType type,
                                 @Param("active") Boolean active,
                                 @Param("query") String query,
                                 Pageable pageable);

    @Modifying
    @Query("update NumberingScheme n set n.defaultScheme = false where n.companyId = :companyId " +
            "and n.documentType = :type and n.id <> :excludedId and n.defaultScheme = true")
    int clearDefault(@Param("companyId") UUID companyId, @Param("type") DocumentType type,
                     @Param("excludedId") UUID excludedId);

    @Modifying
    @Query(value = """
            INSERT INTO numbering_schemes
                (id, company_id, code, name, document_type, series, pattern, reset_period,
                 initial_value, active, default_scheme, created_at, updated_at, version)
            VALUES
                (:id, :companyId, :code, :name, :documentType, :series, :pattern, 'YEARLY',
                 1, TRUE, TRUE, :now, :now, 0)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int ensureDefault(@Param("id") UUID id, @Param("companyId") UUID companyId,
                      @Param("code") String code, @Param("name") String name,
                      @Param("documentType") String documentType, @Param("series") String series,
                      @Param("pattern") String pattern, @Param("now") Instant now);
}
