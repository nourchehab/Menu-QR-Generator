package com.restaurant.admin.model;

import jakarta.persistence.*;

@Entity
@Table(name = "branch_menu_items")
public class BranchMenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /**
     * If non-null, this row overrides (or hides) a restaurant-level MenuItem.
     * If null, this is a branch-only item (not inherited).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_item_id", nullable = true)
    private MenuItem parentItem;

    /** When true, the inherited item is suppressed from this branch's menu. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean hidden = false;

    // ── Overridden / branch-only fields ──────────────────────────────────────
    @Column
    private String name;

    @Column
    private String description;

    @Column
    private Double price;

    @Column
    private String category;

    /** S3 / upload path for a branch-specific photo override. */
    @Column
    private String photoPath;

    // ── Constructors ──────────────────────────────────────────────────────────
    public BranchMenuItem() {}

    /** Factory: hide an inherited item for this branch. */
    public static BranchMenuItem hide(Branch branch, MenuItem parent) {
        BranchMenuItem bmi = new BranchMenuItem();
        bmi.branch     = branch;
        bmi.parentItem = parent;
        bmi.hidden     = true;
        return bmi;
    }

    /** Factory: override an inherited item for this branch. */
    public static BranchMenuItem override(Branch branch, MenuItem parent,
                                          String name, String description,
                                          Double price, String category) {
        BranchMenuItem bmi = new BranchMenuItem();
        bmi.branch      = branch;
        bmi.parentItem  = parent;
        bmi.hidden      = false;
        bmi.name        = name;
        bmi.description = description;
        bmi.price       = price;
        bmi.category    = category;
        return bmi;
    }

    /** Factory: branch-only item (no parent). */
    public static BranchMenuItem branchOnly(Branch branch,
                                            String name, String description,
                                            Double price, String category) {
        BranchMenuItem bmi = new BranchMenuItem();
        bmi.branch      = branch;
        bmi.parentItem  = null;
        bmi.hidden      = false;
        bmi.name        = name;
        bmi.description = description;
        bmi.price       = price;
        bmi.category    = category;
        return bmi;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public Branch getBranch()                  { return branch; }
    public void setBranch(Branch branch)       { this.branch = branch; }

    public MenuItem getParentItem()            { return parentItem; }
    public void setParentItem(MenuItem p)      { this.parentItem = p; }

    public boolean isHidden()                  { return hidden; }
    public void setHidden(boolean hidden)      { this.hidden = hidden; }

    public String getName()                    { return name; }
    public void setName(String name)           { this.name = name; }

    public String getDescription()             { return description; }
    public void setDescription(String d)       { this.description = d; }

    public Double getPrice()                   { return price; }
    public void setPrice(Double price)         { this.price = price; }

    public String getCategory()                { return category; }
    public void setCategory(String category)   { this.category = category; }

    public String getPhotoPath()               { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    /** Convenience: is this an override of an inherited item? */
    public boolean isOverride()  { return parentItem != null && !hidden; }

    /** Convenience: is this a brand-new branch-only item? */
    public boolean isBranchOnly() { return parentItem == null; }
}