from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from dotenv import load_dotenv
import os
import logging
from typing import List, Optional
from .ai_service import category_assistant

# Load environment variables
load_dotenv()

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Create FastAPI app
app = FastAPI(
    title="FlavorFrame AI Service",
    description="AI-powered menu item categorization using Groq + Llama & Langchain",
    version="1.0.0"
)

# Add CORS (allow Spring Boot to call this service)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8081"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ===== PYDANTIC REQUEST/RESPONSE MODELS =====

class CategorizeRequest(BaseModel):
    """Request to categorize a menu item"""
    item_name: str = Field(..., description="Name of the menu item")
    description: str = Field(..., description="Description of the item")
    price: float = Field(..., gt=0, description="Price of the item (must be > 0)")
    restaurant_id: str = Field(..., description="Restaurant ID")
    branch_id: str = Field(..., description="Branch ID")


class CategoryOption(BaseModel):
    """A category suggestion option"""
    category: str
    confidence: float = Field(ge=0.0, le=1.0, description="Confidence score 0-1")


class CategorizeResponse(BaseModel):
    """Response with category suggestion"""
    category: str = Field(..., description="Suggested category")
    confidence: float = Field(ge=0.0, le=1.0, description="Confidence score")
    reasoning: str = Field(..., description="Why this category was chosen")
    alternatives: List[str] = Field(default_factory=list, description="Alternative categories")


class ChatRequest(BaseModel):
    """Request to chat about menu categorization"""
    message: str = Field(..., description="User's question or request")
    restaurant_id: str = Field(..., description="Restaurant ID")
    branch_id: str = Field(..., description="Branch ID")


class ChatResponse(BaseModel):
    """Response from chat endpoint"""
    reply: str = Field(..., description="AI's response about categorization")
    restaurant_id: str
    branch_id: str


class LoadMenuDataRequest(BaseModel):
    """Request to load menu items for a branch"""
    restaurant_id: str = Field(..., description="Restaurant ID")
    branch_id: str = Field(..., description="Branch ID")
    menu_items: List[dict] = Field(..., description="List of menu items with name, category, description")


# ===== HEALTH CHECK ENDPOINTS =====

@app.get("/health")
def health_check():
    """Check if service is alive"""
    return {
        "status": "alive",
        "service": "flavorframe-ai",
        "version": "1.0.0"
    }

@app.get("/status")
def status():
    """Get service status and config"""
    return {
        "service": "flavorframe-ai",
        "version": "1.0.0",
        "ai_model": os.getenv("AI_MODEL", "not-configured"),
        "spring_boot_url": os.getenv("SPRING_BOOT_BASE_URL", "not-configured")
    }

# ===== AI CATEGORIZATION ENDPOINTS =====

@app.post("/api/ai/categorize", response_model=CategorizeResponse)
def categorize_menu_item(request: CategorizeRequest):
    """
    Categorize a menu item using AI
    
    - **item_name**: Name of the menu item
    - **description**: Detailed description
    - **price**: Item price
    - **restaurant_id**: Which restaurant
    - **branch_id**: Which branch (each branch has its own categorization context)
    
    Returns category suggestion with confidence score and alternatives
    """
    try:
        logger.info(f"Categorizing '{request.item_name}' for restaurant {request.restaurant_id}, branch {request.branch_id}")
        
        result = category_assistant.categorize_menu_item(
            item_name=request.item_name,
            description=request.description,
            price=request.price,
            restaurant_id=request.restaurant_id,
            branch_id=request.branch_id
        )
        
        return CategorizeResponse(**result)
        
    except Exception as e:
        logger.error(f"Error in categorize endpoint: {e}")
        raise HTTPException(status_code=500, detail=f"Categorization failed: {str(e)}")


@app.post("/api/ai/chat", response_model=ChatResponse)
def chat_about_categories(request: ChatRequest):
    """
    Chat with AI about menu categorization
    
    - **message**: Your question or request about categories
    - **restaurant_id**: Which restaurant
    - **branch_id**: Which branch (branch-specific context and memory)
    
    Returns AI's response in the context of this branch's menu
    """
    try:
        logger.info(f"Chat for restaurant {request.restaurant_id}, branch {request.branch_id}: {request.message[:50]}...")
        
        reply = category_assistant.chat_about_categories(
            user_message=request.message,
            restaurant_id=request.restaurant_id,
            branch_id=request.branch_id
        )
        
        return ChatResponse(
            reply=reply,
            restaurant_id=request.restaurant_id,
            branch_id=request.branch_id
        )
        
    except Exception as e:
        logger.error(f"Error in chat endpoint: {e}")
        raise HTTPException(status_code=500, detail=f"Chat failed: {str(e)}")


@app.post("/api/ai/load-menu-data")
def load_menu_data(request: LoadMenuDataRequest):
    """
    Load menu items for a branch to use as few-shot examples
    
    - **restaurant_id**: Which restaurant
    - **branch_id**: Which branch
    - **menu_items**: List of menu items (should include name, category, description)
    
    This data is used to provide better context for categorization within this branch
    """
    try:
        logger.info(f"Loading {len(request.menu_items)} menu items for restaurant {request.restaurant_id}, branch {request.branch_id}")
        
        category_assistant.load_restaurant_data(
            restaurant_id=request.restaurant_id,
            branch_id=request.branch_id,
            menu_items=request.menu_items
        )
        
        return {
            "status": "success",
            "message": f"Loaded {len(request.menu_items)} menu items",
            "restaurant_id": request.restaurant_id,
            "branch_id": request.branch_id
        }
        
    except Exception as e:
        logger.error(f"Error loading menu data: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to load menu data: {str(e)}")


@app.post("/api/ai/fetch-branch-context")
async def fetch_branch_context(restaurant_id: str, branch_id: str):
    """
    Fetch fresh menu data from Spring Boot backend for a specific branch
    
    - **restaurant_id**: Which restaurant
    - **branch_id**: Which branch to fetch context for
    
    This updates the AI's knowledge of this branch's menu in real-time
    """
    try:
        logger.info(f"Fetching context for restaurant {restaurant_id}, branch {branch_id}")
        
        success = await category_assistant.fetch_restaurant_context(
            restaurant_id=restaurant_id,
            branch_id=branch_id
        )
        
        if success:
            return {
                "status": "success",
                "message": "Branch context fetched and updated",
                "restaurant_id": restaurant_id,
                "branch_id": branch_id
            }
        else:
            raise HTTPException(status_code=502, detail="Failed to fetch from Spring Boot backend")
            
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error fetching branch context: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch context: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
