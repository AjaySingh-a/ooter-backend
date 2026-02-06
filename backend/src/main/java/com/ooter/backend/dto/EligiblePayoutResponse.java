package com.ooter.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligiblePayoutResponse {
    private Long bookingId;
    private String orderId;
    private String siteName;
    private double settlementAmount;
    /** Phase 1 = 25% on live+proof, 2 = 25% at mid, 3 = 50% at end */
    private int phase;
    /** Amount to pay in this phase (25% or 25% or 50% of settlementAmount) */
    private double amount;
    private String dueCondition;
}
