"""
Langchain service for AI-powered menu item categorization using Google Gemini
With memory, restaurant context learning, and database integration
"""

from langchain_google_genai import ChatGoogleGenerativeAI
import json
import logging
import os
import httpx
from typing import Optional, List, Dict, Any
from datetime import datetime
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

logger = logging.getLogger(__name__)

class CategoryAssistant:
    """AI assistant for categorizing menu items and answering restaurant questions
    
    Features:
    - Conversation memory per restaurant
    - Restaurant-specific menu data learning
    - Integration with Spring Boot backend for live data
    - Few-shot learning from existing menu items
    """
    
    def __init__(self) -> None:
        """Initialize the Gemini LLM and memory storage"""
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key:
            raise ValueError("GEMINI_API_KEY not set in .env file")
        
        model_name = os.getenv("AI_MODEL", "gemini-2.5-flash")
        
        self.llm = ChatGoogleGenerativeAI(
            model=model_name,
            api_key=api_key,
            temperature=0.7
        )
        
        # Predefined realistic categories
        self.predefined_categories: List[str] = [
            "Starters", "Appetizers", "Soups", "Salads",
            "Main Courses", "Mains", "Entrees",
            "Sides", "Vegetables",
            "Desserts", "Sweets",
            "Drinks", "Beverages", "Soft Drinks", "Juices", "Coffee", "Tea",
            "Specials", "House Specials",
            "Other"
        ]
        
        # Memory storage: one conversation history dict per restaurant:branch
        self.memories: Dict[str, List[Dict[str, str]]] = {}
        
        # Restaurant data storage: menu items and context per restaurant_id:branch_id
        self.restaurant_data: Dict[str, Dict[str, Any]] = {}
        
        # Spring Boot backend URL
        self.spring_boot_url: str = os.getenv("SPRING_BOOT_BASE_URL", "http://localhost:8081")
    
    def _get_key(self, restaurant_id: str, branch_id: str) -> str:
        """Create a composite key for restaurant+branch"""
        return f"{restaurant_id}:{branch_id}"
    
    def _get_memory(self, restaurant_id: str, branch_id: str) -> List[Dict[str, str]]:
        """Get or create a conversation history for a specific restaurant and branch"""
        key = self._get_key(restaurant_id, branch_id)
        if key not in self.memories:
            self.memories[key] = []
        return self.memories[key]
    
    def load_restaurant_data(self, restaurant_id: str, branch_id: str, menu_items: List[Dict[str, Any]]) -> None:
        """Load menu items for a restaurant branch to use as few-shot examples
        
        Args:
            restaurant_id: Unique restaurant identifier
            branch_id: Unique branch identifier
            menu_items: List of menu items like [{"name": "...", "category": "...", "description": "..."}]
        """
        key = self._get_key(restaurant_id, branch_id)
        if key not in self.restaurant_data:
            self.restaurant_data[key] = {}
        
        self.restaurant_data[key]["menu_items"] = menu_items
        logger.info(f"Loaded {len(menu_items)} menu items for restaurant {restaurant_id}, branch {branch_id}")
    
    async def fetch_restaurant_context(self, restaurant_id: str, branch_id: str) -> bool:
        """Fetch restaurant branch data from Spring Boot backend
        
        Args:
            restaurant_id: Restaurant ID to fetch
            branch_id: Branch ID to fetch data for
            
        Returns:
            True if successful, False if error
        """
        try:
            async with httpx.AsyncClient() as client:
                # Fetch menu items for the specific branch
                response = await client.get(
                    f"{self.spring_boot_url}/api/restaurants/{restaurant_id}/branches/{branch_id}/items",
                    timeout=10
                )
                response.raise_for_status()
                menu_items = response.json()
                
                # Store the fetched data
                key = self._get_key(restaurant_id, branch_id)
                if key not in self.restaurant_data:
                    self.restaurant_data[key] = {}
                
                self.restaurant_data[key]["menu_items"] = menu_items
                self.restaurant_data[key]["last_fetched"] = datetime.now().isoformat()
                
                logger.info(f"Fetched {len(menu_items)} items for restaurant {restaurant_id}, branch {branch_id}")
                return True
                
        except httpx.TimeoutException:
            logger.warning(f"Timeout fetching data for restaurant {restaurant_id}, branch {branch_id}")
            return False
        except httpx.HTTPError as e:
            logger.warning(f"HTTP error fetching data for restaurant {restaurant_id}, branch {branch_id}: {e}")
            return False
        except Exception as e:
            logger.error(f"Error fetching restaurant context: {e}")
            return False
    
    def _get_few_shot_examples(self, restaurant_id: str, branch_id: str, max_examples: int = 3) -> str:
        """Generate few-shot examples from existing menu items in the branch
        
        Args:
            restaurant_id: Restaurant ID
            branch_id: Branch ID
            max_examples: Maximum examples to include
            
        Returns:
            Formatted string with few-shot examples
        """
        key = self._get_key(restaurant_id, branch_id)
        if key not in self.restaurant_data:
            return ""
        
        menu_items = self.restaurant_data[key].get("menu_items", [])
        if not menu_items:
            return ""
        
        examples = []
        for item in menu_items[:max_examples]:
            name = item.get("name", "Unknown")
            category = item.get("category", "Unknown")
            examples.append(f"- '{name}' → {category}")
        
        if examples:
            return "\nExamples from this restaurant's menu:\n" + "\n".join(examples)
        return ""
    
    def _get_restaurant_context(self, restaurant_id: Optional[str], branch_id: Optional[str]) -> str:
        """Build restaurant branch context string for prompts
        
        Args:
            restaurant_id: Restaurant ID
            branch_id: Branch ID
            
        Returns:
            Formatted context string
        """
        if not restaurant_id or not branch_id:
            return ""
        
        key = self._get_key(restaurant_id, branch_id)
        if key not in self.restaurant_data:
            return ""
        
        data = self.restaurant_data[key]
        context_lines = []
        
        if "name" in data:
            context_lines.append(f"Restaurant: {data['name']}")
        
        if "branch_name" in data:
            context_lines.append(f"Branch: {data['branch_name']}")
        
        if "categories" in data:
            context_lines.append(f"Existing categories: {', '.join(data['categories'])}")
        
        few_shot = self._get_few_shot_examples(restaurant_id, branch_id)
        if few_shot:
            context_lines.append(few_shot)
        
        return "\n".join(context_lines) if context_lines else ""

    def categorize_menu_item(self, item_name: str, description: str, price: float, restaurant_id: Optional[str] = None, branch_id: Optional[str] = None) -> dict:
        """Use Gemini to suggest the best category for a menu item
        
        Args:
            item_name: Name of the menu item
            description: Description of the item
            price: Price of the item
            restaurant_id: Optional restaurant ID for context and memory
            branch_id: Optional branch ID for context and memory
            
        Returns:
            dict with category, confidence, reasoning, and alternatives
        """
        # Build restaurant context
        restaurant_context = self._get_restaurant_context(restaurant_id, branch_id) if (restaurant_id and branch_id) else ""
        
        prompt = f"""{restaurant_context}

Analyze this menu item and suggest the BEST category from the available list.

Available categories: {", ".join(self.predefined_categories)}

Menu Item:
- Name: {item_name}
- Description: {description}
- Price: ${price}

Respond ONLY with valid JSON in this exact format (no markdown, no extra text):
{{
    "category": "chosen_category_from_list",
    "confidence": 0.85,
    "reasoning": "brief explanation why this category fits",
    "alternatives": ["alt_category_1", "alt_category_2"]
}}

Important:
- "category" MUST be one of the available categories
- "confidence" must be between 0.0 and 1.0
- Keep reasoning under 50 words
- alternatives should be 2-3 real alternatives from the list
"""
        
        try:
            response = self.llm.invoke(prompt)
            response_text = str(response.content).strip() if response.content else ""
            
            if not response_text:
                logger.warning(f"Empty response from Gemini for item: {item_name}")
                raise ValueError("Empty response from model")
            
            # Extract JSON from response (handle markdown code blocks if present)
            if "```json" in response_text:
                response_text = response_text.split("```json")[1].split("```")[0].strip()
            elif "```" in response_text:
                response_text = response_text.split("```")[1].split("```")[0].strip()
            
            # Extract JSON from response
            result = json.loads(response_text)
            
            # Validate the response has required fields
            if "category" not in result or "confidence" not in result:
                raise ValueError("Invalid response format from Gemini")
            
            # If we have restaurant_id and branch_id, add this categorization to memory
            if restaurant_id and branch_id:
                memory = self._get_memory(restaurant_id, branch_id)
                memory.append({"role": "user", "content": f"Categorize: {item_name}"})
                memory.append({"role": "assistant", "content": f"Suggested: {result.get('category')} (confidence: {result.get('confidence')})"})
            
            return result
            
        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse Gemini response as JSON: {e}")
            logger.debug(f"Response was: {response_text}")
            return {
                "category": "Other",
                "confidence": 0.0,
                "reasoning": "Could not parse AI response. Using default category.",
                "alternatives": ["Specials", "Main Courses"]
            }
        except Exception as e:
            error_msg = str(e).lower()
            # Handle Gemini API quota/rate limit errors gracefully
            if "429" in error_msg or "quota" in error_msg or "resource_exhausted" in error_msg:
                logger.warning(f"Gemini API quota exceeded: {e}")
                return {
                    "category": "Other",
                    "confidence": 0.0,
                    "reasoning": "AI service temporarily unavailable. Please try again later.",
                    "alternatives": ["Specials", "Main Courses"]
                }
            else:
                logger.error(f"Error in categorization: {e}")
                return {
                    "category": "Other",
                    "confidence": 0.0,
                    "reasoning": "Could not categorize item. Using default category.",
                    "alternatives": ["Specials", "Main Courses"]
                }

    def chat_about_categories(self, user_message: str, restaurant_id: Optional[str] = None, branch_id: Optional[str] = None, restaurant_context: Optional[Dict[str, Any]] = None) -> str:
        """Chat with AI about menu categorization
        
        Args:
            user_message: User's question
            restaurant_id: Optional restaurant ID for memory tracking
            branch_id: Optional branch ID for memory tracking
            restaurant_context: Optional legacy context dict (for backwards compatibility)
            
        Returns:
            AI response about categorization
        """
        # Build context from restaurant_id and branch_id or legacy context
        context_info = ""
        if restaurant_id and branch_id:
            context_info = self._get_restaurant_context(restaurant_id, branch_id)
        elif restaurant_context:
            restaurant_name = restaurant_context.get("name", "Restaurant")
            categories = restaurant_context.get("categories", [])
            if categories:
                context_info = f"Restaurant: {restaurant_name}\nExisting categories: {', '.join(categories)}"
        
        system_prompt = f"""You are a helpful assistant specializing in menu categorization for restaurants.
{context_info}

Help restaurant owners organize their menu items into logical categories. Provide practical, friendly advice.
Keep responses concise and actionable."""
        
        full_message = f"{system_prompt}\n\nUser: {user_message}"
        
        try:
            response = self.llm.invoke(full_message)
            response_text = response.content
            
            # If we have restaurant_id and branch_id, add to memory
            if restaurant_id and branch_id:
                memory = self._get_memory(restaurant_id, branch_id)
                memory.append({"role": "user", "content": user_message})
                memory.append({"role": "assistant", "content": response_text})
            
            return response_text
            
        except Exception as e:
            logger.error(f"Error in chat: {e}")
            return f"I encountered an error processing your request: {str(e)}"


# Singleton instance
category_assistant = CategoryAssistant()
