"""
Unit tests for CategoryAssistant using pytest
Tests AI categorization, memory management, and restaurant context handling
"""
import pytest
from unittest.mock import Mock, patch, AsyncMock, MagicMock
import json
from datetime import datetime
import sys
import os

# Add parent directory to path for imports
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from app.ai_service import CategoryAssistant


class TestCategoryAssistantInit:
    """Tests for CategoryAssistant initialization"""

    def test_init_with_valid_api_key(self):
        """Test successful initialization with valid API key"""
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'test-key-123'}):
            with patch('app.ai_service.ChatGoogleGenerativeAI'):
                assistant = CategoryAssistant()
                
                assert assistant is not None
                assert assistant.memories == {}
                assert assistant.restaurant_data == {}
                assert assistant.spring_boot_url == "http://localhost:8081"

    def test_init_with_custom_model(self):
        """Test initialization with custom model name"""
        with patch.dict(os.environ, {
            'GEMINI_API_KEY': 'test-key-123',
            'AI_MODEL': 'gemini-2.5-pro'
        }):
            with patch('app.ai_service.ChatGoogleGenerativeAI') as mock_chat:
                assistant = CategoryAssistant()
                
                # Verify that ChatGoogleGenerativeAI was called with correct model
                mock_chat.assert_called_once()
                call_kwargs = mock_chat.call_args[1]
                assert call_kwargs['model'] == 'gemini-2.5-pro'

    def test_init_missing_api_key(self):
        """Test that initialization fails without API key"""
        with patch.dict(os.environ, {}, clear=True):
            with pytest.raises(ValueError, match="GEMINI_API_KEY not set"):
                CategoryAssistant()

    def test_predefined_categories(self):
        """Test that predefined categories are populated"""
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'test-key'}):
            with patch('app.ai_service.ChatGoogleGenerativeAI'):
                assistant = CategoryAssistant()
                
                assert len(assistant.predefined_categories) > 0
                assert "Main Courses" in assistant.predefined_categories
                assert "Desserts" in assistant.predefined_categories
                assert "Beverages" in assistant.predefined_categories
                assert "Other" in assistant.predefined_categories

    def test_custom_spring_boot_url(self):
        """Test initialization with custom Spring Boot URL"""
        with patch.dict(os.environ, {
            'GEMINI_API_KEY': 'test-key',
            'SPRING_BOOT_BASE_URL': 'https://production-server.com'
        }):
            with patch('app.ai_service.ChatGoogleGenerativeAI'):
                assistant = CategoryAssistant()
                
                assert assistant.spring_boot_url == 'https://production-server.com'


class TestGetKey:
    """Tests for _get_key method"""

    @pytest.fixture
    def assistant(self):
        """Create a test assistant instance"""
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'test-key'}):
            with patch('app.ai_service.ChatGoogleGenerativeAI'):
                return CategoryAssistant()

    def test_get_key_format(self, assistant):
        """Test that key is formatted correctly"""
        key = assistant._get_key("1", "branch-a")
        assert key == "1:branch-a"

    def test_get_key_different_branches(self, assistant):
        """Test keys for different branches"""
        key1 = assistant._get_key("1", "branch-a")
        key2 = assistant._get_key("1", "branch-b")
        
        assert key1 != key2
        assert key1 == "1:branch-a"
        assert key2 == "1:branch-b"

    def test_get_key_string_inputs(self, assistant):
        """Test that key works with string inputs"""
        key = assistant._get_key("restaurant-123", "main-branch")
        assert "restaurant-123" in key
        assert "main-branch" in key


class TestMemoryManagement:
    """Tests for memory management (_get_memory)"""

    @pytest.fixture
    def assistant(self):
        """Create a test assistant instance"""
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'test-key'}):
            with patch('app.ai_service.ChatGoogleGenerativeAI'):
                return CategoryAssistant()

    def test_get_memory_creates_new(self, assistant):
        """Test that get_memory creates new entry if not exists"""
        memory = assistant._get_memory("1", "branch-a")
        
        assert memory == []
        assert "1:branch-a" in assistant.memories

    def test_get_memory_retrieves_existing(self, assistant):
        """Test that get_memory retrieves existing memory"""
        # First call
        memory1 = assistant._get_memory("1", "branch-a")
        memory1.append({"role": "user", "content": "test message"})
        
        # Second call should return same list
        memory2 = assistant._get_memory("1", "branch-a")
        
        assert memory1 is memory2
        assert len(memory2) == 1
        assert memory2[0]["content"] == "test message"

    def test_get_memory_separate_branches(self, assistant):
        """Test that memory is separate for different branches"""
        mem_a = assistant._get_memory("1", "branch-a")
        mem_b = assistant._get_memory("1", "branch-b")
        
        mem_a.append({"role": "user", "content": "branch-a message"})
        
        assert len(mem_a) == 1
        assert len(mem_b) == 0


class TestLoadRestaurantData:
    """Tests for load_restaurant_data"""

    @pytest.fixture
    def assistant(self):
        """Create a test assistant instance"""
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'test-key'}):
            with patch('app.ai_service.ChatGoogleGenerativeAI'):
                return CategoryAssistant()

    def test_load_restaurant_data_success(self, assistant, sample_menu_items):
        """Test successful loading of restaurant data"""
        assistant.load_restaurant_data("1", "branch-a", sample_menu_items)
        
        key = "1:branch-a"
        assert key in assistant.restaurant_data
        assert assistant.restaurant_data[key]["menu_items"] == sample_menu_items
        assert len(assistant.restaurant_data[key]["menu_items"]) == 3

    def test_load_restaurant_data_empty_items(self, assistant):
        """Test loading with empty menu items"""
        assistant.load_restaurant_data("1", "branch-a", [])
        
        key = "1:branch-a"
        assert len(assistant.restaurant_data[key]["menu_items"]) == 0

    def test_load_restaurant_data_overwrites(self, assistant, sample_menu_items):
        """Test that loading new data overwrites old data"""
        assistant.load_restaurant_data("1", "branch-a", sample_menu_items)
        
        new_items = [{"name": "New Item", "category": "Specials"}]
        assistant.load_restaurant_data("1", "branch-a", new_items)
        
        key = "1:branch-a"
        assert len(assistant.restaurant_data[key]["menu_items"]) == 1
        assert assistant.restaurant_data[key]["menu_items"][0]["name"] == "New Item"


class TestCategorizeMenuItem:
    """Tests for categorize_menu_item"""

    @pytest.fixture
    def assistant(self):
        """Create a test assistant instance"""
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'test-key'}):
            with patch('app.ai_service.ChatGoogleGenerativeAI') as mock_chat:
                return CategoryAssistant()

    def test_categorize_success(self, assistant, sample_categorization_response):
        """Test successful categorization"""
        response_json = json.dumps(sample_categorization_response)
        mock_response = Mock()
        mock_response.content = response_json
        assistant.llm.invoke.return_value = mock_response
        
        result = assistant.categorize_menu_item(
            "Tandoori Chicken",
            "Grilled chicken marinated in yogurt",
            250.0
        )
        
        assert result["category"] == "Main Courses"
        assert result["confidence"] == 0.95
        assert "Chicken" in result["reasoning"] or "chicken" in result["reasoning"]
        assert len(result["alternatives"]) > 0

    def test_categorize_with_restaurant_context(self, assistant, sample_categorization_response):
        """Test categorization with restaurant and branch context"""
        response_json = json.dumps(sample_categorization_response)
        mock_response = Mock()
        mock_response.content = response_json
        assistant.llm.invoke.return_value = mock_response
        
        result = assistant.categorize_menu_item(
            "Tandoori Chicken",
            "Grilled chicken",
            250.0,
            restaurant_id="1",
            branch_id="branch-a"
        )
        
        # Verify memory was updated
        memory = assistant._get_memory("1", "branch-a")
        assert len(memory) == 2
        assert memory[0]["role"] == "user"
        assert memory[1]["role"] == "assistant"

    def test_categorize_json_with_markdown(self, assistant):
        """Test categorization handles JSON with markdown code blocks"""
        json_response = {"category": "Desserts", "confidence": 0.88, "reasoning": "Sweet item", "alternatives": ["Sweets"]}
        markdown_json = f"```json\n{json.dumps(json_response)}\n```"
        
        mock_response = Mock()
        mock_response.content = markdown_json
        assistant.llm.invoke.return_value = mock_response
        
        result = assistant.categorize_menu_item("Gulab Jamun", "Sweet balls", 80.0)
        
        assert result["category"] == "Desserts"
        assert result["confidence"] == 0.88

    def test_categorize_invalid_json(self, assistant):
        """Test categorization handles invalid JSON"""
        mock_response = Mock()
        mock_response.content = "Not valid JSON"
        assistant.llm.invoke.return_value = mock_response
        
        result = assistant.categorize_menu_item("Test Item", "Description", 100.0)
        
        # Should fallback to "Other"
        assert result["category"] == "Other"
        assert result["confidence"] == 0.0

    def test_categorize_empty_response(self, assistant):
        """Test categorization handles empty response"""
        mock_response = Mock()
        mock_response.content = ""
        assistant.llm.invoke.return_value = mock_response
        
        result = assistant.categorize_menu_item("Test", "Test", 100.0)
        
        assert result["category"] == "Other"
        assert result["confidence"] == 0.0

    def test_categorize_missing_fields(self, assistant):
        """Test categorization handles incomplete response"""
        incomplete_json = json.dumps({"category": "Main Courses"})  # missing confidence
        mock_response = Mock()
        mock_response.content = incomplete_json
        assistant.llm.invoke.return_value = mock_response
        
        result = assistant.categorize_menu_item("Test", "Test", 100.0)
        
        assert result["category"] == "Other"


class TestChatAboutCategories:
    """Tests for chat_about_categories"""

    @pytest.fixture
    def assistant(self):
        """Create a test assistant instance"""
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'test-key'}):
            with patch('app.ai_service.ChatGoogleGenerativeAI'):
                return CategoryAssistant()

    def test_chat_success(self, assistant):
        """Test successful chat"""
        mock_response = Mock()
        mock_response.content = "Appetizers and starters are great ways to begin a meal."
        assistant.llm.invoke.return_value = mock_response
        
        result = assistant.chat_about_categories("How should I organize appetizers?")
        
        assert "Appetizers" in result
        assert len(result) > 0

    def test_chat_with_restaurant_context(self, assistant):
        """Test chat with restaurant context"""
        mock_response = Mock()
        mock_response.content = "Recommended categories: Starters, Mains, Desserts, Drinks"
        assistant.llm.invoke.return_value = mock_response
        
        result = assistant.chat_about_categories(
            "What categories should we use?",
            restaurant_id="1",
            branch_id="branch-a"
        )
        
        assert len(result) > 0
        # Verify memory was updated
        memory = assistant._get_memory("1", "branch-a")
        assert len(memory) == 2

    def test_chat_with_legacy_context(self, assistant):
        """Test chat with legacy restaurant_context parameter"""
        mock_response = Mock()
        mock_response.content = "Great question about menu organization!"
        assistant.llm.invoke.return_value = mock_response
        
        legacy_context = {"name": "Test Restaurant", "categories": ["Starters", "Mains"]}
        result = assistant.chat_about_categories(
            "How do I organize my menu?",
            restaurant_context=legacy_context
        )
        
        assert len(result) > 0

    def test_chat_error_handling(self, assistant):
        """Test chat error handling"""
        assistant.llm.invoke.side_effect = Exception("API Error")
        
        result = assistant.chat_about_categories("Test question")
        
        assert "error" in result.lower()


class TestGetFewerShotExamples:
    """Tests for _get_few_shot_examples"""

    @pytest.fixture
    def assistant(self):
        """Create a test assistant instance"""
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'test-key'}):
            with patch('app.ai_service.ChatGoogleGenerativeAI'):
                return CategoryAssistant()

    def test_get_few_shot_with_items(self, assistant, sample_menu_items):
        """Test few-shot example generation with menu items"""
        assistant.load_restaurant_data("1", "branch-a", sample_menu_items)
        
        examples = assistant._get_few_shot_examples("1", "branch-a", max_examples=2)
        
        assert len(examples) > 0
        assert "Tandoori Chicken" in examples or "Gulab Jamun" in examples
        assert "→" in examples

    def test_get_few_shot_no_data(self, assistant):
        """Test few-shot with no data"""
        examples = assistant._get_few_shot_examples("1", "nonexistent")
        
        assert examples == ""

    def test_get_few_shot_empty_items(self, assistant):
        """Test few-shot with empty items list"""
        assistant.load_restaurant_data("1", "branch-a", [])
        
        examples = assistant._get_few_shot_examples("1", "branch-a")
        
        assert examples == ""


class TestGetRestaurantContext:
    """Tests for _get_restaurant_context"""

    @pytest.fixture
    def assistant(self):
        """Create a test assistant instance"""
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'test-key'}):
            with patch('app.ai_service.ChatGoogleGenerativeAI'):
                return CategoryAssistant()

    def test_get_restaurant_context_no_data(self, assistant):
        """Test restaurant context with no data"""
        context = assistant._get_restaurant_context("1", "branch-a")
        
        assert context == ""

    def test_get_restaurant_context_with_data(self, assistant, restaurant_context, sample_menu_items):
        """Test restaurant context generation"""
        key = assistant._get_key("1", "branch-a")
        assistant.restaurant_data[key] = restaurant_context
        assistant.restaurant_data[key]["menu_items"] = sample_menu_items
        
        context = assistant._get_restaurant_context("1", "branch-a")
        
        assert "Test Restaurant" in context
        assert "Main Branch" in context
        assert "Examples from this restaurant's menu:" in context

    def test_get_restaurant_context_none_parameters(self, assistant):
        """Test restaurant context with None parameters"""
        context = assistant._get_restaurant_context(None, None)
        
        assert context == ""


class TestFetchRestaurantContext:
    """Tests for fetch_restaurant_context (async)"""

    @pytest.fixture
    def assistant(self):
        """Create a test assistant instance"""
        with patch.dict(os.environ, {'GEMINI_API_KEY': 'test-key'}):
            with patch('app.ai_service.ChatGoogleGenerativeAI'):
                return CategoryAssistant()

    @pytest.mark.asyncio
    async def test_fetch_restaurant_context_success(self, assistant, sample_menu_items):
        """Test successful fetch from Spring Boot"""
        with patch('app.ai_service.httpx.AsyncClient') as mock_client_class:
            mock_client = AsyncMock()
            mock_response = AsyncMock()
            # response.json() is called synchronously, not awaited
            mock_response.json = Mock(return_value=sample_menu_items)
            mock_response.raise_for_status = Mock()
            # Mock the async context manager
            mock_client.__aenter__.return_value = mock_client
            mock_client.__aexit__.return_value = None
            # Mock the async get call
            mock_client.get = AsyncMock(return_value=mock_response)
            mock_client_class.return_value = mock_client
            
            result = await assistant.fetch_restaurant_context("1", "branch-a")
            
            assert result is True
            key = "1:branch-a"
            assert assistant.restaurant_data[key]["menu_items"] == sample_menu_items
            assert "last_fetched" in assistant.restaurant_data[key]

    @pytest.mark.asyncio
    async def test_fetch_restaurant_context_timeout(self, assistant):
        """Test fetch timeout handling"""
        with patch('app.ai_service.httpx.AsyncClient') as mock_client_class:
            mock_client = AsyncMock()
            mock_client.__aenter__.return_value = mock_client
            mock_client.__aexit__.return_value = None
            mock_client.get.side_effect = Exception("Timeout")
            mock_client_class.return_value = mock_client
            
            result = await assistant.fetch_restaurant_context("1", "branch-a")
            
            assert result is False

    @pytest.mark.asyncio
    async def test_fetch_restaurant_context_http_error(self, assistant):
        """Test fetch HTTP error handling"""
        with patch('app.ai_service.httpx.AsyncClient') as mock_client_class:
            mock_client = AsyncMock()
            mock_client.__aenter__.return_value = mock_client
            mock_client.__aexit__.return_value = None
            mock_client.get.side_effect = Exception("404 Not Found")
            mock_client_class.return_value = mock_client
            
            result = await assistant.fetch_restaurant_context("1", "branch-a")
            
            assert result is False
