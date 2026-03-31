# FlavorFrame - Components & Data Flow Diagram

## 1. High-Level Component Relationships

```
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                      USER (Restaurant Admin)                     ┃
┗━━━━━━━━━━━━━━━━━━━┬━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                     │ HTTPS
                     ▼
        ┏━━━━━━━━━━━━━━━━━━━━━━━━┓
        ┃   Thymeleaf Templates  ┃
        ┃   (HTML + CSS + JS)    ┃
        ┗━━━━━━━┬────────────────┘
                │ HTTP Form/REST
                ▼
        ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
        ┃     Spring Boot Controllers & Services  ┃
        ┃  Port 8081                               ┃
        ┃                                          ┃
        ┃  ├─ MenuItemController                  ┃
        ┃  ├─ AiCategoryService                   ┃
        ┃  ├─ RestaurantService                   ┃
        ┃  ├─ BranchService                       ┃
        ┃  └─ SimpleUserService                   ┃
        ┗━━━┬───────────┬───────────────────┬────┬┛
            │           │                   │    │
    REST    │    JDBC   │        HTTP       │ S3 │
  (REST)    │   JPA     │      REST         │    │
            ▼           ▼                   ▼    ▼
    ┌──────────┐  ┌──────────────┐  ┌──────────────┐ ┌────────────┐
    │  Python  │  │ PostgreSQL   │  │  AWS S3      │ │ Google     │
    │ FastAPI  │  │ Database     │  │  (Images)    │ │ OAuth2     │
    │ (8001)   │  │ (AWS RDS)    │  └──────────────┘ └────────────┘
    │          │  │              │
    │ Groq     │  │ Tables:      │
    │ Llama    │  │ • users      │
    │ LLM      │  │ • restaurants│    ┌──────────────┐
    │          │  │ • branches   │◄───┤ Gmail SMTP   │
    │ Routes:  │  │ • menu_items │    │ (Email OTP)  │
    │ ├─health │  │ • categories │    └──────────────┘
    │ ├─categorize
    │ ├─chat   │  │ • tags       │
    │ ├─load   │  │              │
    │ menu    └──────────────────┘
    └──────────┘
```

---

## 2. Feature-to-Component Mapping

### Authentication & Users
```
Feature: User Registration & Login
  └─ Components:
     ├─ SimpleUserController + PageController
     ├─ SimpleUserService (hash password, verify)
     ├─ CustomAuthenticationProvider (form login)
     ├─ CustomOAuth2UserService (Google OAuth2)
     ├─ SimpleUserRepository (JPA)
     ├─ EmailService (send OTP)
     └─ Database: simple_users table

Flow:
  User clicks "Sign Up"
   ↓
  SignupController renders form
   ↓
  POST /auth/register → SimpleUserService
   ↓
  Email OTP sent via Gmail SMTP
   ↓
  User enters OTP → EmailVerificationService
   ↓
  Account created, redirect to restaurant setup
```

---

### Menu Item Management
```
Feature: Create & Categorize Menu Items
  └─ Components:
     ├─ MenuItemController
     ├─ MenuItemService
     ├─ MenuItemRepository
     ├─ S3PhotoStorageService (image upload)
     ├─ QrCodeService (generate QR)
     ├─ AiCategoryService (AI suggestions)
     └─ Database: menu_items, categories tables

Flow:
  User navigates: /manageitems
   ↓
  MenuItemController renders template
   ↓
  User fills form (name, description, price, photo)
   ↓
  POST /api/items
   ├─ Image → S3 (get URL)
   ├─ Item saved to DB
   └─ Return item ID

(Optional) User clicks "Get AI Suggestion"
   ↓
  MenuItemController.getAiCategorization(itemId)
   ├─ Fetch item from DB
   ├─ Call AiServiceClient.categorizeMenuItem()
   │   ├─ HTTP POST to Python: /api/ai/categorize
   │   ├─ Python processes with Groq LLM
   │   └─ Returns: category, confidence, reasoning
   ├─ Save suggestion to DB
   │   (menu_items.suggested_category = "Main Courses")
   │   (menu_items.ai_confidence = 0.95)
   └─ Return suggestion to frontend

User clicks "Accept Suggestion"
   ↓
  MenuItemController.acceptAiSuggestion(itemId)
   ├─ AiCategoryService.acceptAiSuggestion()
   │   ├─ Get suggested category "Main Courses"
   │   ├─ CategoryService.findOrCreateCategory()
   │   │   ├─ Query DB for category
   │   │   └─ If not exists, CREATE new category
   │   ├─ Link MenuItem → Category
   │   └─ Save to DB
   └─ Return success
```

---

### Public Menu Display
```
Feature: Share Menu via QR Code
  └─ Components:
     ├─ PublicMenuController (no auth required)
     ├─ QrController
     ├─ QrCodeService (ZXing library)
     ├─ MenuItemRepository
     ├─ RestaurantRepository
     └─ Static templates (menu-preview.html, etc.)

Flow:
  Restaurant admin creates menu items
   ↓
  QrCodeService generates PNG
   QR code points to: https://example.com/menu/{restaurantId}
   ↓
  Customer scans QR code with phone
   ↓
  Browser opens: /menu/{restaurantId}
   ↓
  PublicMenuController renders menu
   ├─ Query: RestaurantRepository.findById()
   ├─ Query: MenuItemRepository.findByRestaurant()
   ├─ Apply theme: primary_color, layout
   └─ Display items with S3 image URLs
   
  Customer sees:
   ├─ Restaurant name & logo (from S3)
   ├─ Menu categories
   ├─ Each item with photo, price, description
   └─ Can filter by category, search, etc.
```

---

### AI Categorization (NEW)
```
Feature: AI Menu Item Suggestions
  └─ Architecture:
     ┌─────────────────────────────────────────┐
     │   Spring Boot (Port 8081)                │
     │                                          │
     │   MenuItemController                    │
     │   ├─ POST /items/{id}/categorize        │
     │   └─ POST /items/{id}/accept-suggestion│
     │          ↓                               │
     │   AiCategoryService                     │
     │   ├─ categorizeAndSaveMenuItem()         │
     │   └─ acceptAiSuggestion()               │
     │          ↓                               │
     │   AiServiceClient (REST HTTP)           │
     └────────────┬────────────────────────────┘
                  │ HTTP POST/GET
                  ▼
     ┌─────────────────────────────────────────┐
     │   Python FastAPI (Port 8001)             │
     │                                          │
     │   main.py routes:                        │
     │   ├─ GET /health                        │
     │   │   → Check if service alive          │
     │   │                                      │
     │   ├─ POST /api/ai/categorize            │
     │   │   Input: item_name, description,    │
     │   │          price, restaurant_id,      │
     │   │          branch_id                  │
     │   │                                      │
     │   │   Processing:                        │
     │   │   ├─ Load restaurant context        │
     │   │   │  (fetch existing items for      │
     │   │   │   few-shot learning)            │
     │   │   └─ Call Groq LLM:                 │
     │   │      "Categorize: Tandoori Chicken" │
     │   │                                      │
     │   │   Output: {                          │
     │   │     "category": "Main Courses",      │
     │   │     "confidence": 0.95,              │
     │   │     "reasoning": "Grilled chicken.."│
     │   │     "alternatives": [...]           │
     │   │   }                                  │
     │   │                                      │
     │   ├─ POST /api/ai/chat                  │
     │   │   → Conversational Q&A              │
     │   │                                      │
     │   ├─ POST /api/ai/load-menu-data        │
     │   │   → Load existing items for context │
     │   │                                      │
     │   └─ POST /api/ai/fetch-branch-context  │
     │       → Fetch from Spring Boot          │
     │                                          │
     │   AI Service:                            │
     │   └─ CategoryAssistant (Langchain)      │
     │      ├─ Memory per restaurant:branch    │
     │      ├─ Conversation history            │
     │      └─ Few-shot learning from menu     │
     │                                          │
     │   LLM Model:                             │
     │   └─ Groq API + Llama LLM               │
     │      (fast inference, free tier)        │
     └─────────────────────────────────────────┘

Data Flow (Detailed):

1. Admin uploads "Tandoori Chicken Tikka"
   ├─ POST /api/items
   ├─ Spring Bot saves to DB
   └─ Returns item ID = 42

2. Admin clicks "Get AI Suggestion"
   ├─ GET /api/items/42/categorize
   │  (triggers:)
   ├─ Spring Bot fetches item #42
   ├─ Calls AiServiceClient.categorizeMenuItem()
   │  └─ Sends HTTP POST to Python:
   │     POST http://localhost:8001/api/ai/categorize
   │     Body: {
   │       "item_name": "Tandoori Chicken Tikka",
   │       "description": "Chicken marinated in yogurt and spices",
   │       "price": 450.00,
   │       "restaurant_id": "rest-123",
   │       "branch_id": "branch-a"
   │     }
   │
   │  (Python processes:)
   │  ├─ Load existing items from Spring Bot
   │  │  (GET /api/restaurants/rest-123/items?branchId=branch-a)
   │  ├─ Build prompt:
   │  │  "Based on this restaurant's menu:
   │  │   [list of 5-10 existing items + categories]
   │  │   Categorize: Tandoori Chicken Tikka
   │  │   Choose from: [Starters, Appetizers, ...]"
   │  ├─ Call Groq Llama LLM
   │  ├─ Parse response (JSON)
   │  └─ Return:
   │     {
   │       "category": "Starters",
   │       "confidence": 0.92,
   │       "reasoning": "Indian grilled starter dish, served as appetizer",
   │       "alternatives": ["Appetizers", "Main Courses"]
   │     }
   │
   ├─ Spring Bot receives response
   ├─ Saves to DB:
   │  UPDATE menu_items
   │  SET suggested_category = 'Starters',
   │      ai_confidence = 0.92,
   │      ai_reasoning = 'Indian grilled starter...',
   │      ai_analyzed_at = NOW()
   │  WHERE id = 42;
   │
   └─ Returns to UI: Show suggestion with "Accept" button

3. Admin clicks "Accept Suggestion"
   ├─ POST /api/items/42/accept-suggestion
   │  (triggers:)
   ├─ Spring Bot fetches item #42
   ├─ Gets suggested_category = "Starters"
   ├─ Calls CategoryService.findOrCreateCategory("Starters")
   │  ├─ Query: SELECT * FROM categories WHERE name='Starters'
   │  ├─ If NOT found:
   │  │  └─ INSERT INTO categories (restaurant_id, name, ...)
   │  │     VALUES (rest-123, 'Starters', ...)
   │  └─ Return category_id
   │
   ├─ Links item to category:
   │  UPDATE menu_items
   │  SET category = 'Starters',
   │      category_id = <id>,
   │      ai_confidence = 0.92
   │  WHERE id = 42;
   │
   └─ Returns success

4. Menu now shows:
   ├─ Tandoori Chicken Tikka
   ├─ Category: Starters ✓
   ├─ Price: ₹450
   └─ (AI suggested, user confirmed)
```

---

## 3. Database Schema

```
Database: flavorframe (PostgreSQL)

┌─────────────────────────────────────────┐
│          simple_users                   │
├─────────────────────────────────────────┤
│ PK: id (UUID)                           │
│ ForeignKey: -                           │
│                                         │
│ Columns:                                │
│ ├─ email (VARCHAR, unique)              │
│ ├─ password_hash (VARCHAR, bcrypt)      │
│ ├─ full_name (VARCHAR)                  │
│ ├─ oauth_provider (VARCHAR) [Google]    │
│ ├─ oauth_id (VARCHAR)                   │
│ ├─ created_at (TIMESTAMP)               │
│ ├─ updated_at (TIMESTAMP)               │
│ ├─ verified (BOOLEAN)                   │
│ └─ is_active (BOOLEAN)                  │
└─────────────────────────────────────────┘
             ▲
             │ 1:1 owns
             │
┌─────────────────────────────────────────┐
│       restaurants                       │
├─────────────────────────────────────────┤
│ PK: id (UUID)                           │
│ FK: owner_id → simple_users.id          │
│                                         │
│ Columns:                                │
│ ├─ name (VARCHAR)                       │
│ ├─ logo_url (VARCHAR) [S3 URL]          │
│ ├─ primary_color (VARCHAR) [#RRGGBB]    │
│ ├─ description (TEXT)                   │
│ ├─ created_at (TIMESTAMP)               │
│ └─ updated_at (TIMESTAMP)               │
└─────────────────┬───────────────────────┘
                  │ 1:N
                  ▼
┌─────────────────────────────────────────┐
│         branches                        │
├─────────────────────────────────────────┤
│ PK: id (UUID)                           │
│ FK: restaurant_id → restaurants.id      │
│                                         │
│ Columns:                                │
│ ├─ name (VARCHAR)                       │
│ ├─ address (VARCHAR)                    │
│ ├─ status (ENUM: ACTIVE, INACTIVE)      │
│ ├─ created_at (TIMESTAMP)               │
│ └─ updated_at (TIMESTAMP)               │
└─────────────────┬───────────────────────┘
                  │ 1:N
                  ▼
┌──────────────────────────────────────────────┐
│         menu_items                           │
├──────────────────────────────────────────────┤
│ PK: id (UUID)                                │
│ FK: restaurant_id → restaurants.id           │
│ FK: category_id → categories.id (Optional)   │
│                                              │
│ Columns:                                     │
│ ├─ itemName (VARCHAR)                        │
│ ├─ description (TEXT)                        │
│ ├─ price (DECIMAL 10.2)                      │
│ ├─ photo_url (VARCHAR) [S3 URL]              │
│ ├─ category (VARCHAR) [label string]         │
│ │                                            │
│ ├─ suggested_category (VARCHAR) [NEW - AI]  │
│ ├─ ai_confidence (DOUBLE 0-1) [NEW - AI]    │
│ ├─ ai_reasoning (VARCHAR 500) [NEW - AI]    │
│ ├─ ai_analyzed_at (TIMESTAMP) [NEW - AI]    │
│ │                                            │
│ ├─ created_at (TIMESTAMP)                    │
│ ├─ updated_at (TIMESTAMP)                    │
│ └─ is_available (BOOLEAN)                    │
└──────────────────┬───────────────────────────┘
                   │ N:1
                   ▼
┌──────────────────────────────────────────┐
│         categories                       │
├──────────────────────────────────────────┤
│ PK: id (UUID)                            │
│ FK: restaurant_id → restaurants.id       │
│                                          │
│ Columns:                                 │
│ ├─ name (VARCHAR)                        │
│ ├─ color (VARCHAR) [#RRGGBB]             │
│ ├─ display_order (INT)                   │
│ ├─ created_at (TIMESTAMP)                │
│ └─ updated_at (TIMESTAMP)                │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│         tags                             │
├──────────────────────────────────────────┤
│ PK: id (UUID)                            │
│ FK: restaurant_id → restaurants.id       │
│                                          │
│ Columns:                                 │
│ ├─ name (VARCHAR)                        │
│ ├─ created_at (TIMESTAMP)                │
│ └─ updated_at (TIMESTAMP)                │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│         qr_codes                         │
├──────────────────────────────────────────┤
│ PK: id (UUID)                            │
│ FK: restaurant_id → restaurants.id       │
│                                          │
│ Columns:                                 │
│ ├─ menu_url (VARCHAR)                    │
│ ├─ qr_image_url (VARCHAR) [S3 URL]       │
│ ├─ created_at (TIMESTAMP)                │
│ └─ updated_at (TIMESTAMP)                │
└──────────────────────────────────────────┘
```

---

## 4. Request/Response Flow Examples

### Example 1: Get AI Suggestion for Menu Item

**Request**:
```
POST /api/items/42/categorize
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{}
```

**Spring Boot Processing**:
```java
1. Authenticate user (JWT token)
2. Fetch MenuItem with ID 42
3. Verify user owns this restaurant
4. Call AiServiceClient.categorizeMenuItem():
   {
     "item_name": "Tandoori Chicken",
     "description": "Grilled chicken marinated in yogurt",
     "price": 500.0,
     "restaurant_id": "rest-123",
     "branch_id": "branch-a"
   }
5. Python service processes and returns response
6. Save suggested_category to DB
7. Return to client
```

**Python Processing**:
```python
1. Receive categorization request
2. Load restaurant context (few-shot examples)
3. Build prompt for Groq Llama
4. Call LLM with prompt
5. Parse JSON response
6. Return categorization
```

**Response**:
```json
{
  "success": true,
  "itemId": 42,
  "itemName": "Tandoori Chicken",
  "suggestedCategory": "Main Courses",
  "confidence": 0.95,
  "reasoning": "Grilled chicken marinated in yogurt and spices, served as primary protein dish",
  "alternatives": ["Appetizers", "Starters"]
}
```

---

### Example 2: Accept AI Suggestion

**Request**:
```
POST /api/items/42/accept-suggestion
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{}
```

**Processing**:
```java
1. Authenticate user
2. Fetch MenuItem with ID 42
3. Get suggested_category = "Main Courses"
4. Find or create category:
   - Query: SELECT * FROM categories 
     WHERE restaurant_id='rest-123' AND name='Main Courses'
   - If not found: INSERT new category
5. Update menu item:
   UPDATE menu_items 
   SET category='Main Courses', 
       category_id=<id>,
       ai_confidence=0.95
   WHERE id=42
6. Return success
```

**Response**:
```json
{
  "success": true,
  "message": "AI suggestion accepted",
  "itemName": "Tandoori Chicken",
  "category": "Main Courses",
  "confidence": 0.95
}
```

---

## 5. Frontend Component Breakdown

### Pages (Server-rendered via Thymeleaf)

| Page | URL | Purpose | Key Templates |
|------|-----|---------|--------|
| **Login** | `/login` | User authentication | login.html |
| **Sign Up** | `/signup` | User registration | signup.html |
| **Dashboard** | `/dashboard` | Admin home | dashboard.html |
| **Enter Items** | `/enteritems` | Add menu items | enteritems.html |
| **Manage Items** | `/manageitems` | Edit/delete items, AI suggestions | manageitems.html |
| **Menu Preview** | `/menu/{restaurantId}` | Public menu view | menu-preview.html |
| **Menu Theme** | `/menu/theme` | Customize colors | menu-theme.html |
| **QR Page** | `/qr-page` | QR generation | qr-page.html |
| **Settings** | `/settings` | User profile, restaurant settings | settings.html |

### Key Frontend Features

1. **Authentication**
   - Email/password login
   - Google OAuth2 button
   - OTP verification
   - Password reset

2. **Menu Management**
   - Form for item details (name, description, price)
   - Photo upload (processed by Spring Bot, stored in S3)
   - Category dropdown (editable)
   - **NEW**: "Get AI Suggestion" button
   - **NEW**: Preview of AI suggestion with "Accept" button

3. **Public Menu Display**
   - Restaurant name & logo
   - Menu categories
   - Items with photos & prices
   - Category filtering
   - Search functionality

4. **Admin Dashboard**
   - Restaurant overview
   - Branch management
   - Item count, category breakdown
   - Revenue/analytics (if implemented)

---

## 6. Deployment Architecture Decision Tree

```
┌─ Am I in MVP/POC phase?
│  ├─ YES → Use Docker + AWS App Runner (Option B)
│  │        Estimated time: 2 weeks
│  │        Cost: $100-150/month
│  │
│  └─ NO → Do you need auto-scaling?
│     ├─ YES → Use Kubernetes (Option C)
│     │        Estimated time: 4+ weeks
│     │        Cost: $300+/month
│     │
│     └─ NO → Traditional servers (Option A)
│            Estimated time: 1 week
│            Cost: $200-300/month
```

---

## 7. Key Metrics to Monitor Post-Deployment

1. **Service Health**
   - Spring Boot uptime
   - Python AI service availability
   - Database connection pool health

2. **Performance**
   - API response time (target: < 2s)
   - AI categorization time (target: < 5s)
   - Image upload time (target: < 10s)
   - Page load time (target: < 3s)

3. **Business Metrics**
   - Active restaurants
   - Menu items created
   - AI suggestions accepted/rejected ratio
   - QR code scans (if tracked)

4. **Errors & Alerts**
   - Failed AI categorizations
   - S3 upload failures
   - Database connection issues
   - Authentication failures

---

**Next Steps**: See [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) for detailed setup instructions.
