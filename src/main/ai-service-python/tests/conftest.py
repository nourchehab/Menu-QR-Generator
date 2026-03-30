"""
Pytest configuration and shared fixtures for AI service tests
"""
import pytest
from unittest.mock import Mock, AsyncMock, MagicMock
import os
from dotenv import load_dotenv

# Load test environment
load_dotenv()


@pytest.fixture
def mock_llm():
    """Mock Langchain ChatGoogleGenerativeAI"""
    llm = Mock()
    return llm


@pytest.fixture
def mock_httpx_client():
    """Mock httpx AsyncClient"""
    client = AsyncMock()
    return client


@pytest.fixture
def sample_menu_items():
    """Sample menu items for testing"""
    return [
        {
            "id": 1,
            "name": "Tandoori Chicken",
            "description": "Grilled chicken marinated in yogurt and spices",
            "category": "Main Courses",
            "price": 250.0
        },
        {
            "id": 2,
            "name": "Gulab Jamun",
            "description": "Sweet fried milk-solid balls in sugar syrup",
            "category": "Desserts",
            "price": 80.0
        },
        {
            "id": 3,
            "name": "Mango Lassi",
            "description": "Sweet yogurt-based beverage with mango",
            "category": "Beverages",
            "price": 50.0
        }
    ]


@pytest.fixture
def sample_categorization_response():
    """Sample Gemini categorization response"""
    return {
        "category": "Main Courses",
        "confidence": 0.95,
        "reasoning": "Grilled chicken marinated in yogurt is a main course item",
        "alternatives": ["Chicken Dishes", "Tandoori Items"]
    }


@pytest.fixture
def restaurant_context():
    """Sample restaurant context data"""
    return {
        "name": "Test Restaurant",
        "branch_name": "Main Branch",
        "categories": ["Starters", "Main Courses", "Desserts", "Beverages"],
        "menu_items": [
            {"name": "Samosa", "category": "Starters"},
            {"name": "Butter Chicken", "category": "Main Courses"},
            {"name": "Kheer", "category": "Desserts"}
        ]
    }
