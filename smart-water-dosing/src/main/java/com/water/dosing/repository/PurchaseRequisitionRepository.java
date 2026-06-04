package com.water.dosing.repository;

import com.water.dosing.entity.PurchaseRequisition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long> {

    Optional<PurchaseRequisition> findByRequisitionCode(String requisitionCode);

    List<PurchaseRequisition> findByStatusOrderByCreatedTimeDesc(String status);

    List<PurchaseRequisition> findByChemicalCodeOrderByCreatedTimeDesc(String chemicalCode);

    List<PurchaseRequisition> findByCreatedTimeBetweenOrderByCreatedTimeDesc(Instant start, Instant end);

    @Query("SELECT p FROM PurchaseRequisition p WHERE p.status IN :statuses ORDER BY p.createdTime DESC")
    List<PurchaseRequisition> findByStatuses(@Param("statuses") List<String> statuses);

    @Query("SELECT p FROM PurchaseRequisition p WHERE p.chemicalCode = :code AND p.status = :status ORDER BY p.createdTime DESC")
    List<PurchaseRequisition> findPendingByChemicalCode(@Param("code") String chemicalCode, @Param("status") String status);

    @Query("SELECT p.status, COUNT(p) FROM PurchaseRequisition p WHERE p.createdTime >= :start GROUP BY p.status")
    List<Object[]> getStatusStats(@Param("start") Instant start);

    @Query("SELECT p FROM PurchaseRequisition p WHERE p.supplier = :supplier AND p.status = :status AND p.completedTime BETWEEN :start AND :end ORDER BY p.completedTime DESC")
    List<PurchaseRequisition> findBySupplierAndStatusAndCompletedTimeBetween(
            @Param("supplier") String supplier,
            @Param("status") String status,
            @Param("start") Instant startTime,
            @Param("end") Instant endTime);
}
