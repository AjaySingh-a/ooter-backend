package com.ooter.backend.repository;

import com.ooter.backend.entity.ExecutionProofFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutionProofFileRepository extends JpaRepository<ExecutionProofFile, Long> {
    List<ExecutionProofFile> findByBookingId(Long bookingId);
}

