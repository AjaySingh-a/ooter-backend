package com.ooter.backend.dto;

import lombok.Data;

@Data
public class VendorBankDetailsRequest {
    private String accountHolderName;
    private String accountNumber;
    private String ifscCode;
    private String upiId;
}
