package com.ooter.backend.repository;

import com.ooter.backend.entity.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

    // 🔍 Get all files for a booking
    List<UploadedFile> findByBookingId(Long bookingId);

    // (Optional) 🔁 To prevent duplicate uploads based on filename
    Optional<UploadedFile> findByBookingIdAndName(Long bookingId, String name);
}
