package com.fleet.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class PurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long vendorId;
    public Long productId;
    public int qty;
    public double unitCost;
    public String division;
    public String status = "ORDERED";
    public LocalDateTime createdAt;
}
