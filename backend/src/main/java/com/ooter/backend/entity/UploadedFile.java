package com.ooter.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String url; // 🖼️ Cloud/Local file URL

    private String name; // 📎 Optional: original filename

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    @JsonIgnore // prevent Jackson from serializing lazy Hibernate proxies
    private Booking booking; // 🔁 Many files can be attached to one booking
}
