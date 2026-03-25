package com.restaurant.admin.dto;

import java.time.LocalDateTime;

/**
 * DTO for Branch data in API responses
 */
public class BranchDTO {
    private Long id;
    private String branchName;
    private String address;
    private String phone;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructors
    public BranchDTO() {}
    
    public BranchDTO(Long id, String branchName, String address, String phone, boolean isActive, LocalDateTime createdAt) {
        this.id = id;
        this.branchName = branchName;
        this.address = address;
        this.phone = phone;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getBranchName() {
        return branchName;
    }
    
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
