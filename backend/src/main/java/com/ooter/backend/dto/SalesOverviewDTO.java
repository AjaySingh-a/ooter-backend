package com.ooter.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class SalesOverviewDTO {
    private double totalSales;
    private String period;
    private int daysShowing;
    private List<OrderDTO> orders;

    @Data
    public static class OrderDTO {
        private String orderId;
        private String date;
        private double amount;
    }
}