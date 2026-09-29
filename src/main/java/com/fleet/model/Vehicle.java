package com.fleet.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Vehicle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String plateNo;
    public String model;
    public String division;
    public String status = "ACTIVE";
}
