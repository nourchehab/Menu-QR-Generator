package com.restaurant.admin.dto;

import java.time.LocalDateTime;

public class BranchDTO {

    private Long id;
    private String branchName;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public BranchDTO() {}

    public BranchDTO(Long id, String branchName, boolean active, LocalDateTime createdAt) {
        this.id         = id;
        this.branchName = branchName;
        this.active     = active;
        this.createdAt  = createdAt;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }

    public String getBranchName()                { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }

    public boolean isActive()                    { return active; }
    public void setActive(boolean active)        { this.active = active; }

    public LocalDateTime getCreatedAt()          { return createdAt; }
    public void setCreatedAt(LocalDateTime t)    { this.createdAt = t; }

    public LocalDateTime getUpdatedAt()          { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t)    { this.updatedAt = t; }
}