package com.fleet.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Budget {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String division;
    public double allocated;
}
