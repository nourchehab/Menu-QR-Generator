package com.restaurant.admin.dto;

public class MenuIdeaRequest {

    /**
     * e.g. "Italian", "Lebanese", "Fast Food"
     */
    private String cuisineType;

    /**
     * e.g. "cafe", "finedining", "buffet"
     */
    private String restaurantType;

    /**
     * Comma-separated existing categories so AI avoids duplicates
     */
    private String existingCategories;

    /**
     * How many ideas to generate (default 5, max 10)
     */
    private int count = 5;

    public String getCuisineType() {
        return cuisineType;
    }

    public void setCuisineType(String cuisineType) {
        this.cuisineType = cuisineType;
    }

    public String getRestaurantType() {
        return restaurantType;
    }

    public void setRestaurantType(String restaurantType) {
        this.restaurantType = restaurantType;
    }

    public String getExistingCategories() {
        return existingCategories;
    }

    public void setExistingCategories(String existingCategories) {
        this.existingCategories = existingCategories;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = Math.min(Math.max(count, 1), 10);
    }
}
