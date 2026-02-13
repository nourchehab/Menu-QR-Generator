package com.restaurant.admin.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "restaurants")
public class Restaurant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String restaurantName;
    
    @Column(nullable = false)
    private String restaurantType; // cafe, buffet, fastfood, finedining
    
    @Column
    private String logoPath; // path to stored logo file
    
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private SimpleUser user;
    
    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MenuItem> menuItems = new ArrayList<>();
    
    // Constructors
    public Restaurant() {}
    
    public Restaurant(String restaurantName, String restaurantType, SimpleUser user) {
        this.restaurantName = restaurantName;
        this.restaurantType = restaurantType;
        this.user = user;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getRestaurantName() {
        return restaurantName;
    }
    
    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName;
    }
    
    public String getRestaurantType() {
        return restaurantType;
    }
    
    public void setRestaurantType(String restaurantType) {
        this.restaurantType = restaurantType;
    }
    
    public String getLogoPath() {
        return logoPath;
    }
    
    public void setLogoPath(String logoPath) {
        this.logoPath = logoPath;
    }
    
    public SimpleUser getUser() {
        return user;
    }
    
    public void setUser(SimpleUser user) {
        this.user = user;
    }
    
    public List<MenuItem> getMenuItems() {
        return menuItems;
    }
    
    public void setMenuItems(List<MenuItem> menuItems) {
        this.menuItems = menuItems;
    }
    
    // Helper method to add menu item
    public void addMenuItem(MenuItem item) {
        menuItems.add(item);
        item.setRestaurant(this);
    }
    
    // Helper method to remove menu item
    public void removeMenuItem(MenuItem item) {
        menuItems.remove(item);
        item.setRestaurant(null);
    }
}