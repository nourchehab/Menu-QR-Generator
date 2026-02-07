package com.restaurant.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/diagnostic")
public class DiagnosticController {

    @Autowired
    private DataSource dataSource;

    /**
     * GET /diagnostic/db-status
     * Returns database connection status and basic info
     */
    @GetMapping("/db-status")
    public ResponseEntity<?> getDbStatus() {
        try (Connection conn = dataSource.getConnection()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "CONNECTED");
            response.put("database", conn.getMetaData().getDatabaseProductName());
            response.put("version", conn.getMetaData().getDatabaseProductVersion());
            response.put("url", conn.getMetaData().getURL());
            response.put("user", conn.getMetaData().getUserName());
            return ResponseEntity.ok(response);
        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "FAILED");
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * GET /diagnostic/tables
     * Lists all tables in the current schema
     */
    @GetMapping("/tables")
    public ResponseEntity<?> getTables() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            // Get all tables in the current schema
            ResultSet tables = metaData.getTables(null, "public", "%", new String[]{"TABLE"});
            
            List<Map<String, Object>> tableList = new ArrayList<>();
            while (tables.next()) {
                Map<String, Object> table = new HashMap<>();
                String tableName = tables.getString("TABLE_NAME");
                table.put("name", tableName);
                
                // Get column info for this table
                ResultSet columns = metaData.getColumns(null, "public", tableName, null);
                List<Map<String, String>> columnList = new ArrayList<>();
                while (columns.next()) {
                    Map<String, String> column = new HashMap<>();
                    column.put("name", columns.getString("COLUMN_NAME"));
                    column.put("type", columns.getString("TYPE_NAME"));
                    column.put("nullable", columns.getString("IS_NULLABLE"));
                    columnList.add(column);
                }
                table.put("columns", columnList);
                tableList.add(table);
                columns.close();
            }
            tables.close();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("tableCount", tableList.size());
            response.put("tables", tableList);
            return ResponseEntity.ok(response);
        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "FAILED");
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * GET /diagnostic/users-count
     * Returns count of users in the database
     */
    @GetMapping("/users-count")
    public ResponseEntity<?> getUsersCount() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as user_count FROM users");
            Map<String, Object> response = new HashMap<>();
            
            if (rs.next()) {
                response.put("status", "SUCCESS");
                response.put("userCount", rs.getInt("user_count"));
            }
            rs.close();
            return ResponseEntity.ok(response);
        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "FAILED");
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * GET /diagnostic/full-health
     * Complete diagnostic check: DB status + tables + user count
     */
    @GetMapping("/full-health")
    public ResponseEntity<?> getFullHealth() {
        try (Connection conn = dataSource.getConnection()) {
            Map<String, Object> response = new HashMap<>();
            
            // 1. DB Info
            Map<String, Object> dbInfo = new HashMap<>();
            dbInfo.put("database", conn.getMetaData().getDatabaseProductName());
            dbInfo.put("version", conn.getMetaData().getDatabaseProductVersion());
            dbInfo.put("url", conn.getMetaData().getURL());
            dbInfo.put("status", "CONNECTED");
            response.put("database", dbInfo);
            
            // 2. Tables
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, "public", "%", new String[]{"TABLE"});
            List<String> tableNames = new ArrayList<>();
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME"));
            }
            tables.close();
            response.put("tables", tableNames);
            response.put("tableCount", tableNames.size());
            
            // 3. User count
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as user_count FROM users");
                if (rs.next()) {
                    response.put("userCount", rs.getInt("user_count"));
                }
                rs.close();
            }
            
            response.put("status", "ALL_CHECKS_PASSED");
            return ResponseEntity.ok(response);
        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "FAILED");
            error.put("error", e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(error);
        }
    }
}
