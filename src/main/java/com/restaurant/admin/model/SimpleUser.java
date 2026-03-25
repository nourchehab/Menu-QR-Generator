package com.restaurant.admin.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "simple_user1") // separate table for simplicity
public class SimpleUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean restaurantSetupComplete = false;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean passwordSet = true;

    @Column(unique = true)
    private String googleSub;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean googleLinked = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Restaurant> restaurants = new ArrayList<>();

    // ===== getters / setters =====
    public Long getId() { return id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isRestaurantSetupComplete() { return restaurantSetupComplete; }
    public void setRestaurantSetupComplete(boolean restaurantSetupComplete) {
        this.restaurantSetupComplete = restaurantSetupComplete;
    }

    public boolean isPasswordSet() { return passwordSet; }
    public void setPasswordSet(boolean passwordSet) { this.passwordSet = passwordSet; }

    public String getGoogleSub() { return googleSub; }
    public void setGoogleSub(String googleSub) { this.googleSub = googleSub; }

    public boolean isGoogleLinked() { return googleLinked; }
    public void setGoogleLinked(boolean googleLinked) { this.googleLinked = googleLinked; }

    public List<Restaurant> getRestaurants() { return restaurants; }
    public void setRestaurants(List<Restaurant> restaurants) { this.restaurants = restaurants; }
}
