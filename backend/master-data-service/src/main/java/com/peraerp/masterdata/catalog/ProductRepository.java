package com.peraerp.masterdata.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
    boolean existsByCompanyIdAndProductGroupId(UUID companyId, UUID productGroupId);
    Optional<Product> findFirstByCompanyIdAndBarcodeIgnoreCase(UUID companyId, String barcode);
    Optional<Product> findFirstByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    /** Mayor número de código interno (EAN-13 con prefijo 20) usado en la empresa; nulo si no hay ninguno. */
    @Query(value = "select max(cast(substring(barcode from 3 for 10) as bigint)) from products " +
            "where company_id = :companyId and barcode ~ '" + InternalBarcode.PATTERN + "'", nativeQuery = true)
    Long findMaxInternalBarcodeSequence(@Param("companyId") UUID companyId);

    @Query("select p from Product p where p.companyId = :companyId and (:query is null " +
            "or lower(p.code) like lower(concat('%', :query, '%')) " +
            "or lower(p.name) like lower(concat('%', :query, '%')) " +
            "or lower(p.barcode) like lower(concat('%', :query, '%')))")
    Page<Product> search(@Param("companyId") UUID companyId, @Param("query") String query, Pageable pageable);
}
