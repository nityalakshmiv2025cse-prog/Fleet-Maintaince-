package com.fleet.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Invoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long customerId;
    public String description;
    public double amount;
    public String division;
    public String status = "UNPAID";
    public LocalDateTime createdAt;
}
