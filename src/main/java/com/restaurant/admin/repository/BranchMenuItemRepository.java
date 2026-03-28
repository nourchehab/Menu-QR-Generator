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

    /** Used for snapshot branches — returns all visible (non-hidden) items. */
    List<BranchMenuItem> findByBranchAndHiddenFalse(Branch branch);

    Optional<BranchMenuItem> findByBranchAndParentItem(Branch branch, MenuItem parentItem);

    List<BranchMenuItem> findByBranchAndParentItemIsNullAndHiddenFalse(Branch branch);

    boolean existsByBranchAndParentItem(Branch branch, MenuItem parentItem);

    Optional<BranchMenuItem> findByIdAndBranch(Long id, Branch branch);

    void deleteAllByBranch(Branch branch);
}