package com.ooter.backend.dto;

import lombok.Data;

@Data
public class VendorRegistrationRequest {
    private String companyName;
    private String email;
    private String designation;
    private String mobile;
    private String gstin;
    private String pan;
    private String cin;
    private String address;
}
