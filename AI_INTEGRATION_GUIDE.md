# Spring Boot ↔ Python AI Service Integration

## Overview

Your FlavorFrame backend now integrates with the Python FastAPI AI service for intelligent menu item categorization using Google Gemini 2.5 Flash.

## Architecture

```
Spring Boot (8081)                  Python FastAPI (8001)
├─ MenuItemController              ├─ GET /health
├─ AiCategoryService              ├─ POST /api/ai/categorize
├─ AiServiceClient (REST)         ├─ POST /api/ai/chat
└─ MenuItem (new AI fields)        ├─ POST /api/ai/load-menu-data
                                   └─ POST /api/ai/fetch-branch-context
```

---

## New Spring Boot Features

### 1. MenuItem Entity Updates

Added 4 new database fields to track AI suggestions:

```sql
-- Persisted in menu_items table:
suggested_category VARCHAR(100)      -- AI's category suggestion
ai_confidence DOUBLE                 -- Confidence score (0-1)
ai_reasoning VARCHAR(500)            -- Why Gemini chose this category
ai_analyzed_at TIMESTAMP             -- When AI last analyzed this item
```

### 2. New Services

#### `AiServiceClient.java`
REST client that calls the Python FastAPI service:
- `categorizeMenuItem(name, description, price, restaurantId, branchId)`
  - Returns: `CategorizeResponse` with category, confidence, reasoning, alternatives
- `isHealthy()` - Check if AI service is available

#### `AiCategoryService.java`
Business logic orchestrator:
- `categorizeAndSaveMenuItem(menuItem, restaurantId, branchId)`
  - Calls AI service and persists results
- `categorizeMenuItemsBatch(items, restaurantId, branchId)`
  - Batch process multiple items
- `acceptAiSuggestion(menuItem)`
  - User accepts AI suggestion and applies it as official category
- `isAiServiceAvailable()` - Health check

### 3. New REST API Endpoints

#### Check AI Service Health
```
GET /api/ai/health

Response: 200 OK
{
  "ai_service_healthy": true,
  "message": "AI service is operational"
}
```

#### Get AI Categorization for One Item
```
POST /api/items/{itemId}/categorize?branchId=branch-a

Headers: Authorization required

Response: 200 OK
{
  "success": true,
  "itemId": 42,
  "itemName": "Tandoori Chicken",
  "suggestedCategory": "Main Courses",
  "confidence": "95%",
  "reasoning": "Grilled chicken marinated... is a substantial protein dish typically served as primary course"
}
```

#### Accept AI Suggestion
```
POST /api/items/{itemId}/accept-suggestion

Headers: Authorization required

Response: 200 OK
{
  "success": true,
  "message": "AI suggestion accepted",
  "itemName": "Tandoori Chicken",
  "category": "Main Courses",
  "confidence": 0.95
}
```

#### Batch Categorize All Items for Restaurant
```
POST /api/restaurants/{restaurantId}/batch-categorize?branchId=branch-a

Headers: Authorization required

Response: 200 OK
{
  "success": true,
  "totalItems": 25,
  "categorized": 24,
  "message": "Categorized 24/25 menu items"
}
```

---

## Configuration

### application.properties

```properties
# AI Service Configuration
ai.service.url=http://localhost:8001
```

For production:
```properties
ai.service.url=https://your-ai-service.com
```

---

## Setup Instructions

### 1. Start Both Services

```bash
# Terminal 1: Start Spring Boot (8081)
cd /home/mohdkat/projects/backend-servicex
mvn spring-boot:run

# Terminal 2: Start Python AI Service (8001)
cd /home/mohdkat/projects/backend-servicex/src/main/ai-service-python
source venv/bin/activate
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

### 2. Run Database Migration

The migration will automatically run on Spring Boot startup:
```sql
-- Adds: suggested_category, ai_confidence, ai_reasoning, ai_analyzed_at
-- Indexes: idx_suggested_category, idx_ai_analyzed_at
```

### 3. Verify Integration

```bash
# Check AI service health
curl http://localhost:8081/api/ai/health

# Expected response:
# {"ai_service_healthy": true, "message": "AI service is operational"}
```

---

## Usage Flow

### Workflow 1: Single Item Categorization

1. **User adds new menu item** (e.g., "Tandoori Chicken")
2. **Click "Get AI Suggestion" button** (in UI)
3. **Request:** `POST /api/items/42/categorize?branchId=branch-a`
4. **Backend:**
   - Calls `AiCategoryService.categorizeAndSaveMenuItem()`
   - Calls `AiServiceClient.categorizeMenuItem()`
   - Sends to Python: item name, description, price, restaurant_id, branch_id
   - Receives: category, confidence, reasoning, alternatives
   - **Saves to DB:** menu_items.suggested_category, ai_confidence, etc.
5. **UI shows suggestion** with confidence % and alternatives
6. **User clicks "Accept"** or manually types different category
7. **Request:** `POST /api/items/42/accept-suggestion`
8. **Backend:** Copies suggested_category → category field
9. **Done!**

### Workflow 2: Batch Categorization

1. **Restaurant admin wants to auto-categorize all 25 items**
2. **Click "Categorize All" button**
3. **Request:** `POST /api/restaurants/1/batch-categorize?branchId=branch-a`
4. **Backend:**
   - Fetches all 25 menu items for restaurant
   - Calls AI for each item (sequential or parallel)
   - Stores results for each
5. **UI shows:** "Categorized 24/25 items"
6. **Admin reviews each suggestion individually**
7. **Accepts suggestions they like, overrides others**

---

## Branch-Specific Categorization

Each **branch** maintains its own:
- **Conversation memory** - AI remembers previous interactions
- **Menu context** - Different menus per branch
- **Category patterns** - Each branch's classification style

Example:
- Branch A (Downtown): "Paneer Butter Masala" → "Specials"
- Branch B (Mall): "Paneer Butter Masala" → "Main Courses"

Send `branchId` parameter to maintain separation.

---

## Error Handling

| Scenario | Response | Action |
|----------|----------|--------|
| AI service down | 503 Service Unavailable | User sees "AI temporarily unavailable" |
| Invalid item | 400 Bad Request | Validate item data before sending |
| Unauthorized | 401 Unauthorized | Check authentication token |
| Wrong restaurant | 403 Forbidden | User can only access their own items |
| Empty suggestion | Returns fallback "Other" | Graceful degradation |

---

## Database Fields Reference

```java
// MenuItem.java
@Column(length = 100)
private String suggestedCategory;  // e.g., "Main Courses"

@Column
private Double aiConfidence;       // e.g., 0.95 (95%)

@Column(length = 500)
private String aiReasoning;        // Explanation from Gemini

@Column
private LocalDateTime aiAnalyzedAt; // When analyzed
```

---

## Frontend Integration Points

### In `branch-item-add.html` or `manageitems.html`

```html
<!-- Get AI Suggestion Button -->
<button onclick="getAiSuggestion(itemId)">Get AI Suggestion</button>

<script>
function getAiSuggestion(itemId) {
    fetch(`/api/items/${itemId}/categorize?branchId=branch-a`, {
        method: 'POST'
    })
    .then(r => r.json())
    .then(data => {
        if (data.success) {
            console.log(`Suggested: ${data.suggestedCategory} (${data.confidence})`);
            console.log(`Reason: ${data.reasoning}`);
            // Show UI with suggestions
        }
    });
}

function acceptSuggestion(itemId) {
    fetch(`/api/items/${itemId}/accept-suggestion`, {
        method: 'POST'
    })
    .then(r => r.json())
    .then(data => {
        if (data.success) {
            console.log(`Applied category: ${data.category}`);
            // Refresh item in list
        }
    });
}
</script>
```

---

## Performance Considerations

1. **AI Service Latency:**
   - First call: ~2-5 seconds (model warming up)
   - Subsequent calls: ~1-2 seconds
   - Timeout: 10 seconds (configured in Python service)

2. **Batch Processing:**
   - 25 items = ~25-50 seconds
   - Consider showing progress bar to user
   - Optional: Process in background job

3. **Database Impact:**
   - New columns shouldn't significantly affect performance
   - Indexes on `suggested_category` and `ai_analyzed_at` for quick queries

---

## Troubleshooting

### "AI service is unavailable"

```bash
# Check Python service is running
curl http://localhost:8001/health

# If down, restart:
cd /home/mohdkat/projects/backend-servicex/src/main/ai-service-python
source venv/bin/activate
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

### Gemini API Not Responding

```bash
# Check .env file
cat /home/mohdkat/projects/backend-servicex/src/main/ai-service-python/.env

# Verify API key and model name:
GEMINI_API_KEY=your_key_here
AI_MODEL=gemini-2.5-flash  # Should be available model
```

### Database Migration Failed

```bash
# Check for syntax errors:
cat src/main/resources/db/migration/V4__Add_AI_Categorization_Fields.sql

# Manually run migration:
psql -h restaurant-admin-db.c3oec40wsyuk.eu-north-1.rds.amazonaws.com \
     -U postgres -d postgres \
     -f src/main/resources/db/migration/V4__Add_AI_Categorization_Fields.sql
```

---

## Next Steps

1. **Update frontend UI** to show "Get AI Suggestion" button
2. **Display confidence % and alternatives** to user
3. **Add progress indicator for batch operations**
4. **Consider caching** AI responses for identical items
5. **Monitor AI service usage** for cost optimization

---

## Files Modified/Created

```
Spring Boot Backend:
├── src/main/java/com/restaurant/admin/
│   ├── model/MenuItem.java (4 new fields)
│   ├── service/AiServiceClient.java (NEW)
│   ├── service/AiCategoryService.java (NEW)
│   └── controller/MenuItemController.java (4 new endpoints)
├── src/main/resources/
│   ├── application.properties (AI service URL config)
│   └── db/migration/
│       └── V4__Add_AI_Categorization_Fields.sql (NEW)

Python AI Backend:
└── src/main/ai-service-python/
    ├── app/
    │   ├── main.py (4 endpoints for AI)
    │   └── ai_service.py (Gemini + Langchain)
    ├── .env (API keys & config)
    └── venv/ (Python virtual environment)
```

---

## Cost Optimization

- **Gemini 2.5 Flash:** ~$0.075 per 1M tokens input
- **Average item:** ~200 tokens
- **1000 items:** ~$0.015
- **Runs only on request** - No continuous charges

---

## Security Notes

1. **API Keys:** Keep `GEMINI_API_KEY` in `.env` file (never commit)
2. **Authorization:** Spring Boot verifies user ownership before calling AI
3. **Input Validation:** Item name/description sanitized before sending to AI
4. **Rate Limiting:** Consider adding rate limits for batch operations
5. **HTTPS:** Use in production for security

---

**Ready to integrate with frontend!** 🚀
