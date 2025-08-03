package com.ooter.backend.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ListingDashboardStatsDTO {
    private long active;
    private long nonActive;
    private long booked;
    private long available;
    private long preBooking;
    private long inventoryPassed;
    private long inventoryFailed;
    private long inProgress;
    private long draft;
    private long error;
}
