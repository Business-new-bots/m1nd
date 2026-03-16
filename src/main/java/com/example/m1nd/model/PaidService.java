package com.example.m1nd.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "paid_services")
public class PaidService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    /**
     * Цена в минимальных единицах Stars (XTR), 100 = 1 звезда.
     */
    @Column(name = "price_units")
    private Integer priceUnits;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "active")
    private boolean active = true;
}

