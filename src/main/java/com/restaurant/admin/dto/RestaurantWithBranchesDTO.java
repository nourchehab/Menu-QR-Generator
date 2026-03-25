package com.restaurant.admin.dto;

import java.util.List;

/**
 * DTO for Restaurant with nested branches in API responses
 */
public class RestaurantWithBranchesDTO {
    private Long id;
    private String restaurantName;
    private String restaurantType;
    private String logoPath;
    private String menuBackgroundColor;
    private boolean isMultiBranch;
    private List<BranchDTO> branches;
    
    // Constructors
    public RestaurantWithBranchesDTO() {}
    
    public RestaurantWithBranchesDTO(Long id, String restaurantName, String restaurantType, String logoPath, 
                                     String menuBackgroundColor, boolean isMultiBranch, List<BranchDTO> branches) {
        this.id = id;
        this.restaurantName = restaurantName;
        this.restaurantType = restaurantType;
        this.logoPath = logoPath;
        this.menuBackgroundColor = menuBackgroundColor;
        this.isMultiBranch = isMultiBranch;
        this.branches = branches;
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
    
    public String getMenuBackgroundColor() {
        return menuBackgroundColor;
    }
    
    public void setMenuBackgroundColor(String menuBackgroundColor) {
        this.menuBackgroundColor = menuBackgroundColor;
    }
    
    public boolean isMultiBranch() {
        return isMultiBranch;
    }
    
    public void setMultiBranch(boolean multiB) {
        isMultiBranch = multiB;
    }
    
    public List<BranchDTO> getBranches() {
        return branches;
    }
    
    public void setBranches(List<BranchDTO> branches) {
        this.branches = branches;
    }
}
