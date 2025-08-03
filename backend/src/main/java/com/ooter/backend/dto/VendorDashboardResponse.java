package com.ooter.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VendorDashboardResponse {
    private int totalSites;
    private int bookedSites;
    private int liveSites;
    private double totalSales;
    private int inventorySold;
    private int newOrders;
    private int pendingRTM;
    private double previousPayment;
    private double upcomingPayment;
    private int siteBooked;
    private int siteAvailable;
    private boolean isVerified;
    private boolean onHold;
}
