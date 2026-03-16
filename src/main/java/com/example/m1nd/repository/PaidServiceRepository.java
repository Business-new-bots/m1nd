package com.example.m1nd.repository;

import com.example.m1nd.model.PaidService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaidServiceRepository extends JpaRepository<PaidService, Long> {

    Optional<PaidService> findByCode(String code);

    List<PaidService> findByActiveTrue();
}

