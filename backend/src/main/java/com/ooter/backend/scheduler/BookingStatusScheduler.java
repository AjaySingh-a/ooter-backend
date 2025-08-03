package com.ooter.backend.scheduler;

import com.ooter.backend.entity.Hoarding;
import com.ooter.backend.entity.HoardingStatus;
import com.ooter.backend.repository.BookingRepository;
import com.ooter.backend.repository.HoardingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingStatusScheduler {

    private final HoardingRepository hoardingRepository;
    private final BookingRepository bookingRepository;

    @Scheduled(cron = "0 0 2 * * ?") // runs daily at 2:00 AM
    public void updateAvailableHoardings() {
        List<Hoarding> bookedHoardings = hoardingRepository.findByStatus(HoardingStatus.BOOKED);

        for (Hoarding h : bookedHoardings) {
            long count = bookingRepository.countActiveOrFutureBookings(h.getId());
            if (count == 0) {
                h.setStatus(HoardingStatus.AVAILABLE);
                hoardingRepository.save(h);
                log.info("✔️ Updated hoarding {} to AVAILABLE", h.getId());
            }
        }
    }
}
