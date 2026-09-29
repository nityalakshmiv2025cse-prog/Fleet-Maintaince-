package com.fleet.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long vehicleId;
    public String tirePosition;
    public String reason;
    public String status = "OPEN";
    public double cost;
    public LocalDateTime createdAt;
}
