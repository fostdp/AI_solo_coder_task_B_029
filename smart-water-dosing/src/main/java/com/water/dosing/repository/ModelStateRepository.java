package com.water.dosing.repository;

import com.water.dosing.entity.ModelState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelStateRepository extends JpaRepository<ModelState, Integer> {

    Optional<ModelState> findTopByOrderByUpdatedAtDesc();

    Optional<ModelState> findTopByModelTypeOrderByUpdatedAtDesc(String modelType);
}
