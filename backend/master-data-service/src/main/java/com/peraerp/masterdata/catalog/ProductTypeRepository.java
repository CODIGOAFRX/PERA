package com.peraerp.masterdata.catalog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ProductTypeRepository extends JpaRepository<ProductType, UUID> {}
