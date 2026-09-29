package com.fleet.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Contact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String name;
    public String kind;
    public String email;
}
