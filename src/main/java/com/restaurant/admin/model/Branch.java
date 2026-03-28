package com.restaurant.admin.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "branches")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String branchName;

    /** Mirrors branchName — kept for DB compatibility. */
    @Column(nullable = false)
    private String address;

    /** Optional — nullable. */
    @Column(nullable = true)
    private String phone;

    /** True for the auto-created main branch. Cannot be deleted. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isMainBranch = false;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean isActive = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BranchMenuItem> branchMenuItems = new ArrayList<>();

    // ── Constructors ──────────────────────────────────────────────────────────

    public Branch() {}

    public Branch(String branchName, Restaurant restaurant) {
        this.branchName    = branchName;
        this.address       = branchName;
        this.phone         = null;
        this.isMainBranch  = false;
        this.restaurant    = restaurant;
        this.isActive      = true;
        this.createdAt     = LocalDateTime.now();
    }

    public Branch(String branchName, Restaurant restaurant, boolean isMainBranch) {
        this.branchName    = branchName;
        this.address       = branchName;
        this.phone         = null;
        this.isMainBranch  = isMainBranch;
        this.restaurant    = restaurant;
        this.isActive      = true;
        this.createdAt     = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getId()                              { return id; }
    public void setId(Long id)                       { this.id = id; }

    public String getBranchName()                    { return branchName; }
    public void setBranchName(String branchName)     {
        this.branchName = branchName;
        this.address    = branchName;
    }

    public String getAddress()                       { return address; }
    public void setAddress(String address)           { this.address = address; }

    public String getPhone()                         { return phone; }
    public void setPhone(String phone)               { this.phone = phone; }

    public boolean isMainBranch()                    { return isMainBranch; }
    public void setMainBranch(boolean mainBranch)    { isMainBranch = mainBranch; }

    public boolean isActive()                        { return isActive; }
    public void setActive(boolean active)            { isActive = active; }

    public LocalDateTime getCreatedAt()              { return createdAt; }
    public void setCreatedAt(LocalDateTime t)        { this.createdAt = t; }

    public LocalDateTime getUpdatedAt()              { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t)        { this.updatedAt = t; }

    public Restaurant getRestaurant()                { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }

    public List<BranchMenuItem> getBranchMenuItems() { return branchMenuItems; }
    public void setBranchMenuItems(List<BranchMenuItem> items) { this.branchMenuItems = items; }
}