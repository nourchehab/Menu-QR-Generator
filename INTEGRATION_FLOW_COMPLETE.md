# AI Integration - Complete Data Flow

## Summary

Your integration now works like this:

### ✅ User clicks "Get AI Suggestion" (Read from DB → Call Microservice)

```
1. MenuItemController.getAiCategorization(itemId)
   ├─ Retrieves MenuItem from DB
   ├─ Verifies user owns this restaurant
   └─ Calls AiCategoryService

2. AiCategoryService.categorizeAndSaveMenuItem()
   ├─ Calls AiServiceClient.categorizeMenuItem()
   │  └─ HTTP POST to Python: /api/ai/categorize
   │     (sends: item_name, description, price, restaurant_id, branch_id)
   │
   ├─ Receives: {category, confidence, reasoning, alternatives}
   │
   └─ SAVES to DB:
      └─ menu_items.suggested_category
      └─ menu_items.ai_confidence
      └─ menu_items.ai_reasoning
      └─ menu_items.ai_analyzed_at

3. Response to Frontend:
   {
     "success": true,
     "itemId": 42,
     "itemName": "Tandoori Chicken",
     "suggestedCategory": "Main Courses",
     "confidence": "95%",
     "reasoning": "Grilled chicken marinated in yogurt..."
   }

4. UI shows suggestion with Accept button
```

---

### ✅ User clicks "Accept Suggestion" (Auto-create Category + Update DB)

```
1. MenuItemController.acceptAiSuggestion(itemId)
   ├─ Retrieves MenuItem from DB
   ├─ Gets suggested_category = "Main Courses"
   └─ Calls AiCategoryService

2. AiCategoryService.acceptAiSuggestion()
   ├─ Gets restaurant from menuItem
   ├─ Calls findOrCreateCategory("Main Courses")
   │
   └─ findOrCreateCategory()
      ├─ Queries: CategoryRepository.findByRestaurantAndNameIgnoreCase()
      │
      ├─ If exists:
      │  └─ Return existing Category entity
      │
      └─ If NOT exists:
         ├─ Create NEW Category: name="Main Courses", restaurant_id=X
         ├─ Insert into DB: INSERT INTO categories(...) VALUES(...)
         └─ Return newly created Category entity

3. Link Category to MenuItem:
   ├─ menuItem.setCategory("Main Courses") // String label
   ├─ menuItem.setCategoryEntity(category) // Entity link
   ├─ Save to DB: UPDATE menu_items SET category=?, category_id=?
   └─ Done!

4. Response to Frontend:
   {
     "success": true,
     "message": "AI suggestion accepted",
     "itemName": "Tandoori Chicken",
     "category": "Main Courses",
     "confidence": 0.95
   }
```

---

## Database State Changes

### Before User Interacts

```sql
-- menu_items table
id  | itemName           | category | suggested_category | ai_confidence | category_id
42  | Tandoori Chicken   | NULL     | NULL               | NULL          | NULL
```

### After "Get AI Suggestion"

```sql
-- AI response saved but NOT applied yet
id  | itemName           | category | suggested_category | ai_confidence | category_id
42  | Tandoori Chicken   | NULL     | "Main Courses"     | 0.95          | NULL
                                     ^^^^^^^ NEW
```

### After "Accept Suggestion"

```sql
-- Category auto-created if needed
-- categories table
id  | restaurant_id | name
8   | 1             | "Main Courses"  ← NEW

-- menu_items updated
id  | itemName           | category       | suggested_category | ai_confidence | category_id
42  | Tandoori Chicken   | "Main Courses" | "Main Courses"     | 0.95          | 8
                           ^^^^^^^ APPLIED                                        ^ LINKED
```

---

## Complete Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     SPRING BOOT (Port 8081)                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                     │
│  MenuItemController                                                 │
│  ├─ GET  /api/items/{id}/categorize       ← "Get suggest"         │
│  └─ POST /api/items/{id}/accept-suggestion ← "Accept suggest"      │
│                           ↓                                         │
│  AiCategoryService                                                  │
│  ├─ categorizeAndSaveMenuItem()                                    │
│  ├─ acceptAiSuggestion()                                           │
│  └─ findOrCreateCategory()  ← AUTO-CREATES CATS IF MISSING        │
│                           ↓                                         │
│  AiServiceClient (REST)                                            │
│  └─ categorizeMenuItem()                                           │
│                    POST http://localhost:8001/api/ai/categorize   │
│                                ↓ ↑                                  │
└────────────────────────────────┼─┼──────────────────────────────────┘
                                 ↓ ↑
┌────────────────────────────────┼─┼──────────────────────────────────┐
│        POSTGRESQL DATABASE     ↓ ↑                                   │
├─────────────────────────────────┼─┘──────────────────────────────────┤
│                                                                       │
│  menu_items                                                          │
│  ├─ id, itemName, itemPrice, category, category_id                │
│  ├─ suggested_category (NEW)     ← AI's suggestion                │
│  ├─ ai_confidence (NEW)          ← Confidence %                   │
│  ├─ ai_reasoning (NEW)           ← Why Gemini chose it            │
│  └─ ai_analyzed_at (NEW)         ← When analyzed                  │
│                                                                     │
│  categories                                                         │
│  ├─ id, name, restaurant_id                                        │
│  └─ (AUTO-CREATED by acceptAiSuggestion if missing)               │
│                                                                     │
└────────────────────────────────────────────────────────────────────┘
                                 ↓ ↑
┌────────────────────────────────┼─┼──────────────────────────────────┐
│     PYTHON FASTAPI (Port 8001) ↓ ↑                                  │
├─────────────────────────────────┼─┘──────────────────────────────────┤
│                                                                       │
│  /api/ai/categorize (POST)                                          │
│  ├─ Input: {item_name, description, price, restaurant_id, ...}   │
│  ├─ Process: Gemini 2.5 Flash LLM                                  │
│  └─ Output: {category, confidence, reasoning, alternatives}       │
│                                                                     │
│  CategoryAssistant                                                  │
│  ├─ In-memory memory per branch (tracks conversation)              │
│  ├─ Per-branch menu context                                        │
│  └─ Few-shot learning from restaurant's existing items            │
│                                                                     │
└───────────────────────────────────────────────────────────────────┘
                                 ↓ ↑
┌───────────────────────────────────────────────────────────────────┐
│        GOOGLE CLOUD - GEMINI 2.5 FLASH API                        │
├───────────────────────────────────────────────────────────────────┤
│  Takes item description → Returns best-fit category               │
│  ~1-2 second response time                                        │
│  Cost: ~$0.000015 per item                                        │
└───────────────────────────────────────────────────────────────────┘
```

---

## API Endpoints

### 1. Get AI Categorization (Read from DB + Call Microservice)

```http
POST /api/items/42/categorize?branchId=branch-a

Authorization: Bearer {token}

Response 200 OK:
{
  "success": true,
  "itemId": 42,
  "itemName": "Tandoori Chicken",
  "suggestedCategory": "Main Courses",
  "confidence": "95%",
  "reasoning": "Grilled chicken marinated in yogurt and spices..."
}

Response 503 Service Unavailable:
{
  "error": "AI service is currently unavailable"
}
```

### 2. Accept Suggestion (Auto-create Category + Update DB)

```http
POST /api/items/42/accept-suggestion

Authorization: Bearer {token}

Response 200 OK:
{
  "success": true,
  "message": "AI suggestion accepted",
  "itemName": "Tandoori Chicken",
  "category": "Main Courses",
  "confidence": 0.95
}

Database Changes:
1. Category table: Inserts new row if "Main Courses" doesn't exist
2. menu_items table: 
   - Updates category = "Main Courses"
   - Links category_id to the category entity
```

### 3. Check AI Service Health

```http
GET /api/ai/health

Response 200 OK:
{
  "ai_service_healthy": true,
  "message": "AI service is operational"
}

Response 200 OK (service down):
{
  "ai_service_healthy": false,
  "message": "AI service is unavailable"
}
```

---

## Code Flow - Query Retrieval

### Step 1: Get AI Suggestion

```java
// MenuItemController.java (line ~460)
@PostMapping("/api/items/{id}/categorize")
public ResponseEntity<?> getAiCategorization(@PathVariable Long id, 
                                              @RequestParam String branchId,
                                              Principal principal) {
    // 1. GET: Retrieves MenuItem from DB (reads item properties)
    MenuItem menuItem = menuItemService.getMenuItemById(id);
    
    // 2. POST: Sends to AI microservice (queries Python service)
    boolean success = aiCategoryService.categorizeAndSaveMenuItem(
        menuItem,           // ItemName, description, price
        restaurant.getId(), // For context
        branchId            // For branch-specific memory
    );
    
    // 3. SAVE: Stores AI response in DB
    // (suggested_category, ai_confidence, ai_reasoning, ai_analyzed_at)
}
```

### Step 2: Query Details Sent to Python Microservice

```java
// AiServiceClient.java (line ~93)
public CategorizeResponse categorizeMenuItem(
    String itemName,        // "Tandoori Chicken"
    String description,     // "Grilled chicken marinated..."
    Double price,          // 250.0
    String restaurantId,   // "1"
    String branchId        // "branch-a"
) {
    // HTTP POST to Python service:
    // POST http://localhost:8001/api/ai/categorize
    // {
    //   "item_name": "Tandoori Chicken",
    //   "description": "Grilled chicken marinated...",
    //   "price": 250.0,
    //   "restaurant_id": "1",
    //   "branch_id": "branch-a"
    // }
}
```

### Step 3: Python Microservice Processes

```python
# Python FastAPI - app/main.py
@app.post("/api/ai/categorize", response_model=CategorizeResponse)
def categorize_menu_item(request: CategorizeRequest):
    # Call CategoryAssistant with branch-specific context
    result = category_assistant.categorize_menu_item(
        item_name=request.item_name,
        description=request.description,
        price=request.price,
        restaurant_id=request.restaurant_id,
        branch_id=request.branch_id    # ← Uses for memory/context
    )
    # Returns: category, confidence, reasoning, alternatives
```

### Step 4: User Accepts - Auto-creates Category

```java
// MenuItemController.java (line ~515)
@PostMapping("/api/items/{id}/accept-suggestion")
public ResponseEntity<?> acceptAiSuggestion(@PathVariable Long id, Principal principal) {
    MenuItem menuItem = menuItemService.getMenuItemById(id);
    
    // Calls AiCategoryService
    boolean success = aiCategoryService.acceptAiSuggestion(menuItem);
}

// AiCategoryService.java (line ~75)
public boolean acceptAiSuggestion(MenuItem menuItem) {
    String suggestedCategoryName = menuItem.getSuggestedCategory(); // "Main Courses"
    
    // AUTO-CREATE IF NOT EXISTS
    Category category = findOrCreateCategory(
        menuItem.getRestaurant(), 
        suggestedCategoryName
    );
    
    // Link and save
    menuItem.setCategory(suggestedCategoryName);
    menuItem.setCategoryEntity(category);
    menuItemRepository.save(menuItem);
}

// findOrCreateCategory() queries DB and creates if needed:
public Category findOrCreateCategory(Restaurant restaurant, String categoryName) {
    // Try to find
    Optional<Category> existing = categoryRepository
        .findByRestaurantAndNameIgnoreCase(restaurant, categoryName);
    
    if (existing.isPresent()) {
        return existing.get(); // ← USE EXISTING
    }
    
    // CREATE NEW if doesn't exist
    Category newCategory = new Category();
    newCategory.setName(categoryName);
    newCategory.setRestaurant(restaurant);
    categoryRepository.save(newCategory); // ← INSERT INTO categories
    
    return newCategory;
}
```

---

## Database Queries Sent

### When Getting Suggestion

```sql
-- 1. Fetch menu item
SELECT * FROM menu_items WHERE id = 42;

-- 2. After AI response, SAVE to DB
UPDATE menu_items 
SET suggested_category = 'Main Courses',
    ai_confidence = 0.95,
    ai_reasoning = 'Grilled chicken marinated...',
    ai_analyzed_at = NOW()
WHERE id = 42;
```

### When Accepting Suggestion

```sql
-- 1. Try to find existing category
SELECT * FROM categories 
WHERE restaurant_id = 1 
  AND LOWER(name) = LOWER('Main Courses');

-- 2. If NOT found, CREATE
INSERT INTO categories (name, restaurant_id, created_at) 
VALUES ('Main Courses', 1, NOW());

-- 3. Link category to menu item
UPDATE menu_items 
SET category = 'Main Courses',
    category_id = 8  -- newly created category
WHERE id = 42;
```

---

## Why This Design?

### ✅ **Two-Step Process**
1. **Get Suggestion** - AI analyzes, human reviews
2. **Accept Suggestion** - Apply only what user approves

### ✅ **Auto-creates Categories**
- No manual category setup needed
- Categories created on-demand as AI suggests them
- Restaurant can start with 0 categories, AI populates them

### ✅ **Branch-Specific**
- Each branch remembers its own preferences
- Same item might get different categories per branch
- Memory stored in Python service per `restaurant_id:branch_id`

### ✅ **Audit Trail**
- `ai_reasoning` shows WHY Gemini chose category
- `ai_confidence` shows how confident it was (95%, 73%, etc)
- `ai_analyzed_at` shows when analysis happened
- Original `suggested_category` kept for reference

### ✅ **Secure**
- Every API call checks user authorization
- Can only categorize items in their own restaurant
- Category links validated via foreign keys

---

## Complete Integration Chain

```
User clicks button
    ↓
Frontend: POST /api/items/42/categorize
    ↓
Spring Boot receives request (MenuItemController)
    ↓
Query #1: SELECT * FROM menu_items WHERE id = 42
    ↓
AiCategoryService.categorizeAndSaveMenuItem()
    ↓
AiServiceClient.categorizeMenuItem()
    ↓
HTTP POST to Python: http://localhost:8001/api/ai/categorize
    ↓
Python receives: {item_name, description, price, restaurant_id, branch_id}
    ↓
CategoryAssistant in Python:
    - Loads branch-specific menu context
    - Calls Gemini API with context
    - Receives category suggestion
    ↓
HTTP Response: {category, confidence, reasoning, alternatives}
    ↓
Query #2: UPDATE menu_items SET suggested_category, ai_confidence, ...
    ↓
Spring Boot returns JSON to Frontend
    ↓
UI displays suggestion with "Accept" button
    ↓
User clicks "Accept"
    ↓
Frontend: POST /api/items/42/accept-suggestion
    ↓
MenuItemController.acceptAiSuggestion()
    ↓
AiCategoryService.acceptAiSuggestion()
    ↓
findOrCreateCategory():
    Query #3: SELECT * FROM categories WHERE restaurant_id=1 AND name=?
    If not found:
        Query #4: INSERT INTO categories (name, restaurant_id, ...)
    ↓
Query #5: UPDATE menu_items SET category=?, category_id=?
    ↓
HTTP Response: {success: true, category: "Main Courses"}
    ↓
Frontend updates UI (item now shows category)
    ↓
Done!
```

---

## Summary

✅ **Get AI Suggestion** = Query item from DB + Call Python microservice + Save response to DB
✅ **Accept Suggestion** = Auto-create category if needed + Link to menu item + Update DB
✅ **All queries detailed** = SELECT, INSERT, UPDATE operations logged
✅ **User flow complete** = Click → See suggestion → Accept → Auto-categorize

Ready to test with your app! 🚀
