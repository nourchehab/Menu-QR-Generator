package com.restaurant.admin.repository;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.BranchMenuItem;
import com.restaurant.admin.model.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchMenuItemRepository extends JpaRepository<BranchMenuItem, Long> {

    List<BranchMenuItem> findByBranch(Branch branch);

    List<BranchMenuItem> findByBranchAndHiddenFalse(Branch branch);

    Optional<BranchMenuItem> findByBranchAndParentItem(Branch branch, MenuItem parentItem);

    Optional<BranchMenuItem> findByIdAndBranch(Long id, Branch branch);

    void deleteAllByBranch(Branch branch);

    /**
     * ✅ Counts how many BranchMenuItem rows still reference a given photoPath.
     * Used before deleting from S3 — only delete if count drops to 0,
     * so snapshot branches that share the same photo URL are never broken.
     */
    long countByPhotoPath(String photoPath);
}