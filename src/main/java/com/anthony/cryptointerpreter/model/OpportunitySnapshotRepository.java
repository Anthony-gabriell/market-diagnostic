package com.anthony.cryptointerpreter.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OpportunitySnapshotRepository extends JpaRepository<OpportunitySnapshot, Long> {

    List<OpportunitySnapshot> findAllByOrderByOpportunityScoreDesc();

    Optional<OpportunitySnapshot> findFirstBySymbolOrderByScannedAtDesc(String symbol);
}
