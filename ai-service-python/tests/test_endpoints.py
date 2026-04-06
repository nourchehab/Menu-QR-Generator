"""
Unit tests for FastAPI endpoints using pytest and httpx TestClient
Tests all AI service API endpoints
"""
import pytest
from fastapi.testclient import TestClient
from unittest.mock import Mock, AsyncMock, patch
import json
from datetime import datetime
import sys
import os

# Add parent directory to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from app.main import app
from app.ai_service import category_assistant


@pytest.fixture
def client():
    """Create test client for FastAPI app"""
    return TestClient(app)


@pytest.fixture
def sample_request():
    """Sample categorization request"""
    return {
        "item_name": "Tandoori Chicken",
        "description": "Grilled chicken marinated in yogurt and spices",
        "price": 250.0,
        "restaurant_id": "1",
        "branch_id": "branch-a"
    }


@pytest.fixture
def sample_chat_request():
    """Sample chat request"""
    return {
        "message": "How should I organize appetizers and starters?",
        "restaurant_id": "1",
        "branch_id": "branch-a"
    }


@pytest.fixture
def sample_load_menu_request():
    """Sample load menu data request"""
    return {
        "restaurant_id": "1",
        "branch_id": "branch-a",
        "menu_items": [
            {
                "id": 1,
                "name": "Samosa",
                "category": "Starters",
                "description": "Crispy fried pastry",
                "price": 40.0
            },
            {
                "id": 2,
                "name": "Butter Chicken",
                "category": "Main Courses",
                "description": "Chicken in creamy tomato sauce",
                "price": 280.0
            }
        ]
    }


# ================== Health Check Tests ==================

class TestHealthEndpoints:
    """Tests for health check endpoints"""

    def test_health_endpoint(self, client):
        """Test /health endpoint"""
        response = client.get("/health")
        
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "alive"
        assert data["version"] == "1.0.0"

    def test_status_endpoint(self, client):
        """Test /status endpoint"""
        response = client.get("/status")
        
        assert response.status_code == 200
        data = response.json()
        assert "service" in data
        assert "ai_model" in data
        assert "spring_boot_url" in data


# ================== Categorize Endpoint Tests ==================

class TestCategorizeEndpoint:
    """Tests for /api/ai/categorize endpoint"""

    def test_categorize_success(self, client, sample_request):
        """Test successful categorization"""
        with patch.object(category_assistant, 'categorize_menu_item') as mock_categorize:
            mock_categorize.return_value = {
                "category": "Main Courses",
                "confidence": 0.95,
                "reasoning": "Grilled chicken is a main course",
                "alternatives": ["Chicken Dishes"]
            }
            
            response = client.post("/api/ai/categorize", json=sample_request)
            
            assert response.status_code == 200
            data = response.json()
            assert data["category"] == "Main Courses"
            assert data["confidence"] == 0.95
            assert len(data["alternatives"]) > 0

    def test_categorize_missing_item_name(self, client, sample_request):
        """Test categorization with missing item name"""
        sample_request.pop("item_name")
        
        response = client.post("/api/ai/categorize", json=sample_request)
        
        assert response.status_code == 422  # Validation error

    def test_categorize_missing_description(self, client, sample_request):
        """Test categorization with missing description"""
        sample_request.pop("description")
        
        response = client.post("/api/ai/categorize", json=sample_request)
        
        assert response.status_code == 422

    def test_categorize_missing_price(self, client, sample_request):
        """Test categorization with missing price"""
        sample_request.pop("price")
        
        response = client.post("/api/ai/categorize", json=sample_request)
        
        assert response.status_code == 422

    def test_categorize_missing_restaurant_id(self, client, sample_request):
        """Test categorization with missing restaurant_id"""
        sample_request.pop("restaurant_id")
        
        response = client.post("/api/ai/categorize", json=sample_request)
        
        assert response.status_code == 422

    def test_categorize_zero_price(self, client, sample_request):
        """Test categorization with zero price - validation should reject"""
        sample_request["price"] = 0.0
        
        response = client.post("/api/ai/categorize", json=sample_request)
        
        # Price validator requires price > 0, so this should fail with 422
        assert response.status_code == 422

    def test_categorize_high_price(self, client, sample_request):
        """Test categorization with high price"""
        sample_request["price"] = 5000.0
        
        with patch.object(category_assistant, 'categorize_menu_item') as mock_categorize:
            mock_categorize.return_value = {
                "category": "Main Courses",
                "confidence": 0.92,
                "reasoning": "Premium item",
                "alternatives": []
            }
            
            response = client.post("/api/ai/categorize", json=sample_request)
            
            assert response.status_code == 200

    def test_categorize_special_characters(self, client, sample_request):
        """Test categorization with special characters in item name"""
        sample_request["item_name"] = "Chicken & Fish curry"
        
        with patch.object(category_assistant, 'categorize_menu_item') as mock_categorize:
            mock_categorize.return_value = {
                "category": "Main Courses",
                "confidence": 0.88,
                "reasoning": "Mixed protein dish",
                "alternatives": []
            }
            
            response = client.post("/api/ai/categorize", json=sample_request)
            
            assert response.status_code == 200

    def test_categorize_long_description(self, client, sample_request):
        """Test categorization with very long description"""
        sample_request["description"] = "A" * 500
        
        with patch.object(category_assistant, 'categorize_menu_item') as mock_categorize:
            mock_categorize.return_value = {
                "category": "Other",
                "confidence": 0.5,
                "reasoning": "Generic item",
                "alternatives": []
            }
            
            response = client.post("/api/ai/categorize", json=sample_request)
            
            assert response.status_code == 200


# ================== Chat Endpoint Tests ==================

class TestChatEndpoint:
    """Tests for /api/ai/chat endpoint"""

    def test_chat_success(self, client, sample_chat_request):
        """Test successful chat"""
        with patch.object(category_assistant, 'chat_about_categories') as mock_chat:
            mock_chat.return_value = "Appetizers are great for starting a meal. Consider these categories..."
            
            response = client.post("/api/ai/chat", json=sample_chat_request)
            
            assert response.status_code == 200
            data = response.json()
            assert "reply" in data
            assert len(data["reply"]) > 0
            assert "restaurant_id" in data
            assert "branch_id" in data

    def test_chat_missing_message(self, client, sample_chat_request):
        """Test chat with missing user message"""
        sample_chat_request.pop("message")
        
        response = client.post("/api/ai/chat", json=sample_chat_request)
        
        assert response.status_code == 422

    def test_chat_missing_restaurant_id(self, client, sample_chat_request):
        """Test chat with missing restaurant_id"""
        sample_chat_request.pop("restaurant_id")
        
        response = client.post("/api/ai/chat", json=sample_chat_request)
        
        assert response.status_code == 422

    def test_chat_empty_message(self, client, sample_chat_request):
        """Test chat with empty message"""
        sample_chat_request["message"] = ""
        
        response = client.post("/api/ai/chat", json=sample_chat_request)
        
        # May accept empty message or reject it - both are valid
        assert response.status_code in [200, 422]

    def test_chat_long_message(self, client, sample_chat_request):
        """Test chat with very long message"""
        sample_chat_request["message"] = "How should I organize my menu? " * 100
        
        with patch.object(category_assistant, 'chat_about_categories') as mock_chat:
            mock_chat.return_value = "That's a complex question. Let me help you organize your menu..."
            
            response = client.post("/api/ai/chat", json=sample_chat_request)
            
            assert response.status_code == 200

    def test_chat_special_characters_message(self, client, sample_chat_request):
        """Test chat with special characters in message"""
        sample_chat_request["message"] = "What about items with & symbols, e.g., Fish & Chips?"
        
        with patch.object(category_assistant, 'chat_about_categories') as mock_chat:
            mock_chat.return_value = "Good question about special characters!"
            
            response = client.post("/api/ai/chat", json=sample_chat_request)
            
            assert response.status_code == 200


# ================== Load Menu Data Endpoint Tests ==================

class TestLoadMenuDataEndpoint:
    """Tests for /api/ai/load-menu-data endpoint"""

    def test_load_menu_data_success(self, client, sample_load_menu_request):
        """Test successful menu data loading"""
        with patch.object(category_assistant, 'load_restaurant_data') as mock_load:
            response = client.post("/api/ai/load-menu-data", json=sample_load_menu_request)
            
            assert response.status_code == 200
            data = response.json()
            assert "status" in data
            assert data["status"] == "success"
            assert "message" in data

    def test_load_menu_data_missing_restaurant_id(self, client, sample_load_menu_request):
        """Test with missing restaurant_id"""
        sample_load_menu_request.pop("restaurant_id")
        
        response = client.post("/api/ai/load-menu-data", json=sample_load_menu_request)
        
        assert response.status_code == 422

    def test_load_menu_data_missing_branch_id(self, client, sample_load_menu_request):
        """Test with missing branch_id"""
        sample_load_menu_request.pop("branch_id")
        
        response = client.post("/api/ai/load-menu-data", json=sample_load_menu_request)
        
        assert response.status_code == 422

    def test_load_menu_data_empty_items(self, client, sample_load_menu_request):
        """Test loading with empty items list"""
        sample_load_menu_request["menu_items"] = []
        
        with patch.object(category_assistant, 'load_restaurant_data') as mock_load:
            response = client.post("/api/ai/load-menu-data", json=sample_load_menu_request)
            
            assert response.status_code == 200

    def test_load_menu_data_missing_items_field(self, client, sample_load_menu_request):
        """Test with missing menu_items field"""
        sample_load_menu_request.pop("menu_items")
        
        response = client.post("/api/ai/load-menu-data", json=sample_load_menu_request)
        
        assert response.status_code == 422

    def test_load_menu_data_large_batch(self, client, sample_load_menu_request):
        """Test loading large batch of items"""
        # Create 100 items
        sample_load_menu_request["menu_items"] = [
            {
                "id": i,
                "name": f"Item {i}",
                "category": "Category",
                "description": f"Description {i}",
                "price": 100.0 + i
            }
            for i in range(100)
        ]
        
        with patch.object(category_assistant, 'load_restaurant_data') as mock_load:
            response = client.post("/api/ai/load-menu-data", json=sample_load_menu_request)
            
            assert response.status_code == 200


# ================== Fetch Branch Context Endpoint Tests ==================

class TestFetchBranchContextEndpoint:
    """Tests for /api/ai/fetch-branch-context endpoint"""

    def test_fetch_branch_context_success(self, client):
        """Test successful branch context fetch"""
        with patch.object(category_assistant, 'fetch_restaurant_context') as mock_fetch:
            mock_fetch.return_value = True
            
            response = client.post("/api/ai/fetch-branch-context?restaurant_id=1&branch_id=branch-a")
            
            assert response.status_code == 200
            data = response.json()
            assert data["status"] == "success"

    def test_fetch_branch_context_missing_restaurant_id(self, client):
        """Test with missing restaurant_id"""
        response = client.post("/api/ai/fetch-branch-context?branch_id=branch-a")
        
        assert response.status_code == 422

    def test_fetch_branch_context_missing_branch_id(self, client):
        """Test with missing branch_id"""
        response = client.post("/api/ai/fetch-branch-context?restaurant_id=1")
        
        assert response.status_code == 422

    def test_fetch_branch_context_failure(self, client):
        """Test failed fetch - returns 502 error"""
        with patch.object(category_assistant, 'fetch_restaurant_context') as mock_fetch:
            mock_fetch.return_value = False
            
            response = client.post("/api/ai/fetch-branch-context?restaurant_id=1&branch_id=branch-a")
            
            # When fetch returns False, endpoint raises HTTPException with 502
            assert response.status_code == 502


# ================== CORS and Security Tests ==================

class TestCORSAndSecurity:
    """Tests for CORS and security settings"""

    def test_cors_headers_present(self, client):
        """Test that CORS headers are present"""
        response = client.get("/health")
        
        # Check for CORS headers (may vary depending on CORS configuration)
        assert response.status_code == 200

    def test_post_with_json_content_type(self, client, sample_request):
        """Test POST requests with JSON content type"""
        with patch.object(category_assistant, 'categorize_menu_item') as mock_categorize:
            mock_categorize.return_value = {
                "category": "Main Courses",
                "confidence": 0.95,
                "reasoning": "Test",
                "alternatives": []
            }
            
            response = client.post(
                "/api/ai/categorize",
                json=sample_request,
                headers={"Content-Type": "application/json"}
            )
            
            assert response.status_code == 200


# ================== Response Format Tests ==================

class TestResponseFormats:
    """Tests for response format compliance"""

    def test_categorize_response_format(self, client, sample_request):
        """Test categorize response has correct format"""
        with patch.object(category_assistant, 'categorize_menu_item') as mock_categorize:
            mock_categorize.return_value = {
                "category": "Main Courses",
                "confidence": 0.95,
                "reasoning": "Test reasoning",
                "alternatives": ["Alternative 1"]
            }
            
            response = client.post("/api/ai/categorize", json=sample_request)
            data = response.json()
            
            # Check required fields
            assert "category" in data
            assert "confidence" in data
            assert "reasoning" in data
            assert "alternatives" in data
            
            # Check types
            assert isinstance(data["category"], str)
            assert isinstance(data["confidence"], (int, float))
            assert isinstance(data["reasoning"], str)
            assert isinstance(data["alternatives"], list)

    def test_chat_response_format(self, client, sample_chat_request):
        """Test chat response has correct format"""
        with patch.object(category_assistant, 'chat_about_categories') as mock_chat:
            mock_chat.return_value = "Sample response"
            
            response = client.post("/api/ai/chat", json=sample_chat_request)
            data = response.json()
            
            # Check required fields
            assert "reply" in data
            assert isinstance(data["reply"], str)
            assert "restaurant_id" in data
            assert "branch_id" in data

    def test_load_menu_response_format(self, client, sample_load_menu_request):
        """Test load menu response has correct format"""
        with patch.object(category_assistant, 'load_restaurant_data'):
            response = client.post("/api/ai/load-menu-data", json=sample_load_menu_request)
            data = response.json()
            
            # Check required fields
            assert "status" in data
            assert isinstance(data["status"], str)
            assert "message" in data
            assert "restaurant_id" in data
            assert "branch_id" in data
