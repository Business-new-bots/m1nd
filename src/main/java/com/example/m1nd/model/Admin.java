package com.example.m1nd.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "admins")
public class Admin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "username", nullable = false, unique = true)
    @JsonProperty("username")
    private String username;
    
    @Column(name = "added_at", nullable = false)
    @JsonProperty("addedAt")
    private LocalDateTime addedAt;
    
    @Column(name = "added_by")
    @JsonProperty("addedBy")
    private String addedBy;
}
