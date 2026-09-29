package com.fleet.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Telemetry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long vehicleId;
    public String tirePosition;
    public double psi;
    public double wearPercent;
    public LocalDateTime readAt;
}
