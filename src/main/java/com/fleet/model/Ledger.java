package com.fleet.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Ledger {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String account;
    public String kind;
    public double debit;
    public double credit;
    public String memo;
    public String division;
    public LocalDateTime postedAt;
}
