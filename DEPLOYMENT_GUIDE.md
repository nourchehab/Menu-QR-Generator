# FlavorFrame Backend-ServiceX - Deployment Guide

**Project**: backend-servicex (FlavorFrame SaaS)  
**Date**: March 31, 2026  
**Current Environment**: Spring Boot 4.0.2, PostgreSQL, Python FastAPI, AWS S3

---

## 1. PROJECT OVERVIEW

### What This Application Does
FlavorFrame is a **Restaurant Management SaaS** that helps restaurant admins:
- Manage multiple restaurant branches with different menus
- Create and organize menu items with categorization
- Generate QR codes linking to digital menus
- Preview menus with theme customization
- **NEW**: Use AI to automatically categorize menu items (powered by Groq Llama)

### Key Business Features
- 🔐 User authentication (email/password + Google OAuth2)
- 🏪 Multi-branch restaurant management
- 🍽️ Menu item management with photos stored in AWS S3
- 🎨 QR code generation for digital menus
- 🤖 **AI-powered menu item categorization** (Groq Llama + Langchain)
- 📊 Admin dashboard with restaurant analytics

---

## 2. ARCHITECTURE OVERVIEW

### System Architecture Diagram
```
┌─────────────────────────────────────────────────────────────┐
│                    CLIENT LAYER (Browser)                   │
│  ├─ Thymeleaf HTML Templates (Server-rendered)              │
│  ├─ Static Assets (CSS, JS, Images)                         │
│  └─ QR Code Display Pages                                   │
└───────────────┬───────────────────────────────────────────┘
                │ HTTPS / HTTP
┌───────────────▼───────────────────────────────────────────┐
│            SPRING BOOT APPLICATION (Port 8081)             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  Controllers (REST + MVC)                             │ │
│  │  ├─ AuthController: Login/Registration/OAuth2         │ │
│  │  ├─ MenuItemController: CRUD operations               │ │
│  │  ├─ AdminDashboardController: Restaurant mgmt         │ │
│  │  ├─ PublicMenuController: Public menu pages           │ │
│  │  ├─ QrController: QR code generation                  │ │
│  │  └─ ClassifyController: AI categorization UI          │ │
│  └───────────────────────────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  Service Layer                                        │ │
│  │  ├─ AiCategoryService: Menu categorization logic      │ │
│  │  ├─ MenuItemService: Item operations                  │ │
│  │  ├─ RestaurantService: Restaurant setup & updates     │ │
│  │  ├─ BranchService: Location management                │ │
│  │  ├─ S3PhotoStorageService: Image upload/storage       │ │
│  │  ├─ QrCodeService: QR generation (ZXing)              │ │
│  │  ├─ AiServiceClient: HTTP client to Python service    │ │
│  │  └─ SimpleUserService: User authentication            │ │
│  └───────────────────────────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  Data Access Layer (JPA/Hibernate)                    │ │
│  │  ├─ SimpleUserRepository                              │ │
│  │  ├─ RestaurantRepository                              │ │
│  │  ├─ BranchRepository                                  │ │
│  │  ├─ MenuItemRepository                                │ │
│  │  ├─ CategoryRepository                                │ │
│  │  └─ TagRepository                                     │ │
│  └───────────────────────────────────────────────────────┘ │
└──────┬────────┬─────────────────┬──────────────────────────┘
       │        │                 │
       │ HTTP   │ JDBC            │ HTTP
       │ REST   │ PostgreSQL       │ REST
       ▼        ▼                 ▼
   ┌────────┐ ┌──────────────┐  ┌──────────────────────────┐
   │ Python │ │ PostgreSQL   │  │  AWS Services            │
   │ FastAPI│ │ Database     │  │  ├─ S3 (Image Storage)   │
   │ (8001) │ │ (RDS)        │  │  └─ RDS (DB Hosting)     │
   │        │ │              │  │                          │
   │ Groq   │ │ Tables:      │  │  Credentials in env vars │
   │ Llama  │ │ ├─ users     │  └──────────────────────────┘
   │ LLM    │ │ ├─ restaurants
   │        │ │ ├─ branches  │
   │ Routes:│ │ ├─ menu_items│
   │ GET    │ │ ├─ categories
   │ /health│ │ └─ tags      │
   │ POST   │ │              │
   │ /api/ai│ │ Hosted:      │
   │ /cat.. │ │ AWS RDS      │
   │ /chat  │ │ (eu-north-1)│
   │ /load- │ │ restaurant- │
   │ menu   │ │ admin-db    │
   └────────┘ └──────────────┘
```

### Component Details

#### Frontend (Browser Client)
- **Rendering**: Server-side (Thymeleaf templates)
- **Static Content**: CSS, JavaScript, Images stored in `/static/`
- **Pages**:
  - Login & Registration (OAuth2 support)
  - Admin Dashboard (restaurants & branches)
  - Menu Item Management (add/edit/delete)
  - Public Menu Preview (with QR code)
  - Theme Customization

#### Backend - Spring Boot (Port 8081)
- **Framework**: Spring Boot 4.0.2
- **Web**: Spring MVC + REST endpoints
- **Security**: Spring Security + OAuth2 Client (Google)

##### Key Controllers:
| Controller | Purpose | Main Endpoints |
|-----------|---------|----------------|
| **AuthController** | User auth, OTP verification | POST /auth/{otp,reset-password,register} |
| **PageController** | Template rendering | GET /dashboard, /enteritems, /manageitems |
| **AdminDashboardController** | Restaurant management | GET /admin/restaurants, POST /api/branches |
| **MenuItemController** | CRUD menu items, AI categorization | GET/POST /api/items, /api/items/{id}/categorize |
| **PublicMenuController** | Public menu display | GET /menu/{restaurantId}, /m/{restaurantId} |
| **QrController** | QR code generation | GET /api/qr/menu, /api/qr/menu/{restaurantId} |

##### Key Services:
| Service | Responsibility |
|---------|-----------------|
| **AiCategoryService** | Calls Python AI service, stores suggestions, applies user acceptance |
| **AiServiceClient** | HTTP REST client for Python FastAPI |
| **RestaurantService** | Setup, logo upload/storage, color settings |
| **MenuItemService** | CRUD items, triggers AI categorization |
| **S3PhotoStorageService** | Upload images to AWS S3, manage URLs |
| **QrCodeService** | Generate QR code PNGs using ZXing |
| **SimpleUserService** | User login, registration, password reset |
| **EmailService** | OTP & verification email delivery |

#### Backend - Python FastAPI (Port 8001)
- **Framework**: FastAPI (async)
- **AI Model**: Groq API + Llama LLM
- **Purpose**: Menu item categorization with AI reasoning

##### Key Features:
- **Categorization Endpoint**: `POST /api/ai/categorize`
  - Input: item_name, description, price, restaurant_id, branch_id
  - Output: category, confidence (0-1), reasoning, alternatives
  - Uses few-shot learning from existing menu items

- **Chat Endpoint**: `POST /api/ai/chat`
  - Conversational Q&A about menu items

- **Context Loading**: 
  - `POST /api/ai/load-menu-data` - Load existing items as examples
  - `POST /api/ai/fetch-branch-context` - Fetch from Spring Boot API

- **Health Check**: `GET /health`

#### Database - PostgreSQL (AWS RDS)
- **Host**: restaurant-admin-db.c3oec40wsyuk.eu-north-1.rds.amazonaws.com
- **Port**: 5432
- **Engine**: PostgreSQL
- **Region**: eu-north-1

##### Core Tables:
```sql
-- Users
simple_users (id, email, password_hash, created_at, oauth_id)

-- Restaurant Management
restaurants (id, owner_id, name, logo_url, primary_color, ...)
branches (id, restaurant_id, name, address, status, ...)

-- Menu Data
menu_items (
  id, restaurant_id, itemName, description, price, category,
  photo_url, suggested_category, ai_confidence, ai_reasoning,
  ai_analyzed_at, category_id, ...
)
categories (id, restaurant_id, name, created_at)
tags (id, restaurant_id, name, created_at)

-- QR Codes
qr_codes (id, restaurant_id, menu_url, qr_image, created_at)
```

#### External Services
- **AWS S3**: Image storage (logos, menu photos, thumbnails)
- **AWS RDS**: PostgreSQL database hosting
- **Google OAuth2**: Third-party authentication
- **Gmail SMTP**: Email delivery (OTP, verification codes)
- **Groq API**: LLM inference for menu categorization

---

## 3. BUILDING & DEPLOYMENT OPTIONS

### Option A: Traditional Deployment (Recommended for Getting Started)

#### Requirements
- Java 17+ (Spring Boot 4.0.2 requires Java 21+)
- Maven 3.6+
- Python 3.10+
- PostgreSQL 12+
- AWS Account (S3 + RDS)

#### Step-by-Step Build & Run

##### 1. Backend (Spring Boot)
```bash
# Build the JAR
mvn clean package

# Run Spring Boot application
java -jar target/backend-servicex-0.0.1-SNAPSHOT.jar

# Alternative: Run via Maven
mvn spring-boot:run
```

**Configuration** (via environment variables):
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://your-rds-host:5432/your_db
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=your_password
export GOOGLE_CLIENT_ID=your_google_client_id
export GOOGLE_CLIENT_SECRET=your_google_secret
export AWS_ACCESS_KEY_ID=your_aws_key
export AWS_SECRET_ACCESS_KEY=your_aws_secret
export S3_BUCKET_NAME=your_s3_bucket
```

##### 2. Frontend (Python AI Service)
```bash
cd src/main/ai-service-python

# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Create .env file with:
# GROQ_API_KEY=your_groq_key
# SPRING_BOOT_BASE_URL=http://localhost:8081
# AI_MODEL=llama-4-scout

# Run FastAPI server
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

**Health Check**:
```bash
curl http://localhost:8001/health
```

#### Result
- **Spring Boot**: http://localhost:8081
- **Python FastAPI**: http://localhost:8001
- **Database**: Connected PostgreSQL

#### Pros
✅ Easy local development  
✅ Full control over configuration  
✅ Fast iteration  
✅ Good for testing

#### Cons
❌ Manual server management  
❌ No auto-scaling  
❌ No built-in failover  

---

### Option B: Docker Containerization (Recommended for Production)

#### Why Docker?
- Consistent development → production environment
- Easy deployment to any cloud platform (AWS, Azure, Google Cloud)
- Container orchestration ready (Kubernetes)
- Version control for infrastructure

#### Build Docker Images

##### 1. Spring Boot Backend Dockerfile
```dockerfile
# Dockerfile (in root directory)
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
```

##### 2. Python FastAPI Dockerfile
```dockerfile
# Dockerfile in /src/main/ai-service-python
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app app/
EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
```

##### 3. Docker Compose (for local multi-container setup)
```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: flavorframe
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  backend:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/flavorframe
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
    depends_on:
      - postgres

  ai-service:
    build:
      context: ./src/main/ai-service-python
      dockerfile: Dockerfile
    ports:
      - "8001:8001"
    environment:
      GROQ_API_KEY: ${GROQ_API_KEY}
      SPRING_BOOT_BASE_URL: http://backend:8081

volumes:
  postgres_data:
```

**Build & Run**:
```bash
docker-compose up -d
```

#### Deploy to AWS (via ECR + ECS)

```bash
# 1. Create ECR repositories
aws ecr create-repository --repository-name flavorframe-backend
aws ecr create-repository --repository-name flavorframe-ai-service

# 2. Build and push images
docker build -t flavorframe-backend .
docker tag flavorframe-backend:latest YOUR_AWS_ACCOUNT.dkr.ecr.eu-north-1.amazonaws.com/flavorframe-backend:latest
docker push YOUR_AWS_ACCOUNT.dkr.ecr.eu-north-1.amazonaws.com/flavorframe-backend:latest

# 3. Deploy to ECS/Fargate (manual via AWS Console or via AWS CLI)
```

#### Deploy to Azure (via Azure Container Registry + App Service)
```bash
# 1. Login to Azure
az login

# 2. Create container registry
az acr create --resource-group myResourceGroup --name myRegistry --sku Basic

# 3. Build and push
az acr build --registry myRegistry --image flavorframe-backend:latest .

# 4. Deploy to Azure App Service or Container Instances
az container create --resource-group myResourceGroup --name flavorframe-backend \
  --image myRegistry.azurecr.io/flavorframe-backend:latest
```

#### Pros
✅ Production-ready packaging  
✅ Multi-platform deployment  
✅ Version control for infrastructure  
✅ Easy rollback  
✅ Scalability ready  

#### Cons
❌ Requires Docker knowledge  
❌ More infrastructure overhead  

---

### Option C: Kubernetes Deployment (Enterprise)

#### Why Kubernetes?
- Auto-scaling based on load
- Self-healing (auto-restart failed pods)
- Rolling deployments (zero downtime)
- Service discovery & load balancing
- Production-grade reliability

#### Kubernetes Manifests

##### 1. Deployment for Spring Boot Backend
```yaml
# backend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flavorframe-backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: flavorframe-backend
  template:
    metadata:
      labels:
        app: flavorframe-backend
    spec:
      containers:
      - name: backend
        image: YOUR_REGISTRY/flavorframe-backend:latest
        ports:
        - containerPort: 8081
        env:
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: db-url
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: username
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
```

##### 2. Service for Backend
```yaml
# backend-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: flavorframe-backend
spec:
  selector:
    app: flavorframe-backend
  ports:
  - port: 80
    targetPort: 8081
  type: LoadBalancer
```

##### 3. Deployment for Python AI Service
```yaml
# ai-service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flavorframe-ai-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: flavorframe-ai-service
  template:
    metadata:
      labels:
        app: flavorframe-ai-service
    spec:
      containers:
      - name: ai-service
        image: YOUR_REGISTRY/flavorframe-ai-service:latest
        ports:
        - containerPort: 8001
        env:
        - name: GROQ_API_KEY
          valueFrom:
            secretKeyRef:
              name: ai-credentials
              key: groq-api-key
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
```

**Deploy**:
```bash
kubectl apply -f backend-deployment.yaml
kubectl apply -f backend-service.yaml
kubectl apply -f ai-service-deployment.yaml
```

#### Pros
✅ Enterprise-grade reliability  
✅ Auto-scaling  
✅ Self-healing  
✅ Global deployment ready  

#### Cons
❌ Complex learning curve  
❌ Operational overhead  
❌ Higher infrastructure costs  

---

## 3.5 FREE DEPLOYMENT OPTIONS

### ⭐ BEST FOR FREE: Railway.app

**Why Railway?**
- ✅ Generous free tier ($5/month credit for first 30 days, then $5/month ongoing)
- ✅ Both Spring Boot and Python services supported
- ✅ PostgreSQL database included
- ✅ One-command deploy from Git
- ✅ Environment variables management built-in
- ✅ Auto-deploys on git push
- ⚠️ Limited RAM (512MB per service)

**Setup (15 minutes)**:

```bash
# 1. Create Railway account at railway.app
# 2. Connect GitHub repo

# 3. Create Spring Boot service:
#    - New Service → GitHub Repo
#    - Select backend-servicex repo
#    - Set environment variables:
#      SPRING_DATASOURCE_URL=postgresql://user:pass@host/db
#      SPRING_DATASOURCE_USERNAME=postgres
#      SPRING_DATASOURCE_PASSWORD=[generated]
#      SPRING_JPA_HIBERNATE_DDL_AUTO=update
#      (other OAuth2, S3 keys)

# 4. Create Python service:
#    - New Service → GitHub Repo
#    - Point to src/main/ai-service-python folder
#    - Set GROQ_API_KEY, SPRING_BOOT_BASE_URL

# 5. Add PostgreSQL:
#    - New Service → Provision PostgreSQL
#    - Railway auto-connects to services
```

**Cost**:
- **Free tier**: $5/month credit (usually covers small deployment)
- **After free**: $0.50/hour compute (pause on inactivity = $0)
- **PostgreSQL**: Included in free tier
- **Total**: ~$0-10/month depending on usage

**Limitations**:
- No free IP address (shared domain only)
- Memory: 512MB per service (Spring Boot might need more)
- Cold starts: 30-60 seconds after inactivity pause
- Database: 10GB limit

**Deploy in 3 steps**:
```bash
# Step 1: Create .railway.json (in root)
{
  "build": {
    "builder": "rails"
  },
  "start": "java -jar target/*.jar"
}

# Step 2: Push to GitHub
git add .
git commit -m "Ready for Railway deployment"
git push

# Step 3: Connect in Railway console & deploy
# (Railway auto-deploys on push)
```

---

### Option 2: Render.com

**Why Render?**
- ✅ Free tier with PostgreSQL
- ✅ Auto-deploys from GitHub
- ✅ 750 free compute hours/month (~1 small service)
- ✅ Good for light traffic
- ⚠️ Slow cold starts (can pause services)

**Cost**:
- **Free tier**: 750 compute hours/month (enough for 1 service 24/7)
- **Extra services**: Additional $7+/month each
- **PostgreSQL**: Up to 4 connections free
- **Total**: ~$0-15/month for 2 services

**Setup**:
```bash
# 1. Connect GitHub at render.com
# 2. Create Web Service
#    - Build Command: mvn package -DskipTests
#    - Start Command: java -jar target/backend-servicex*.jar
# 3. Add PostgreSQL instance (free tier)
# 4. Set environment variables
```

**Drawback**: Limited to 2-3 services on free tier

---

### Option 3: Google Cloud Run (Free Tier)

**Why Google Cloud Run?**
- ✅ 2 million requests/month FREE
- ✅ Scalable (grows with traffic)
- ✅ Low latency (fast cold starts)
- ✅ Good for production
- ⚠️ Database costs extra ($0.50/GB storage)

**Cost**:
- **Cloud Run**: FREE (2M requests + 360k CPU-seconds/month)
- **Cloud SQL PostgreSQL**: ~$10/month (smallest tier)
- **Cloud Storage (S3)**: ~$0.02 per GB stored
- **Total**: ~$10-20/month for production-grade

**Setup Requires Docker**:
```bash
# 1. Build Docker images
docker build -t backend .
docker build -t ai-service ./src/main/ai-service-python

# 2. Deploy to Cloud Run
gcloud run deploy flavorframe-backend --image backend --memory 512Mi
gcloud run deploy flavorframe-ai --image ai-service --memory 1Gi

# 3. Create Cloud SQL PostgreSQL instance
gcloud sql instances create flavorframe-db --tier db-f1-micro

# 4. Deploy via GitHub Actions auto-deploy
```

---

### Option 4: Heroku Alternative - Fly.io

**Why Fly.io?**
- ✅ Free tier: Up to 3 shared-cpu-1x 256MB VMs
- ✅ Global deployment ready
- ✅ Built-in PostgreSQL support
- ✅ Modern container-based
- ✅ Great documentation
- ⚠️ Limited to 3 free instances total

**Cost**:
- **Free tier**: 3 shared VMs (256MB each) + 3GB storage
- **Paid**: $3/month per 1 shared CPU VM
- **Total**: Usually $0 (if you fit in 3 instances)

**Setup**:
```bash
# 1. Install Fly CLI
curl -L https://fly.io/install.sh | sh

# 2. Launch app
fly launch

# 3. Deploy
fly deploy
```

---

## Comparison: Free Tier Services

| Service | Cost | Spring Boot | Python | Database | Easy Setup |
|---------|------|-----------|--------|----------|-----------|
| **Railway** | $5/mo* | ✅ | ✅ | ✅ Free | ⭐⭐⭐⭐⭐ |
| **Render** | Free (750h) | ✅ | ✅ | ✅ Free | ⭐⭐⭐⭐ |
| **Google Cloud Run** | Free + $10DB | ✅ | ✅ | ~$10/mo | ⭐⭐⭐⭐ |
| **Fly.io** | Free (3 VMs) | ✅ | ✅ | ✅ Free | ⭐⭐⭐ |
| **AWS Free Tier** | FREE (1yr) | ✅ | ✅ | ✅ Free | ⭐⭐⭐ |
| **Vercel** | FREE | ❌ API | ✅ Serverless | ❌ | ⭐⭐ |

*Free tier limit applies; pause after free month

---

### Why NOT Vercel (for full stack)?

❌ **Vercel is frontend-focused**:
- Designed for Next.js, React, Static sites
- Doesn't support **long-running Spring Boot processes**
- Python is only via serverless functions (10-30s timeout limit)
- No persistent databases
- No support for background jobs

✅ **What Vercel CAN do**:
- Host **frontend as React SPA** (later migration)
- Host API via serverless functions (REST endpoints only)
- Requires complete restructuring of your app

❌ **Better alternatives for your stack**: Railway, Render, Fly.io, Google Cloud Run

---

## 4. DEPLOYMENT DECISION MATRIX

| Criteria | Railway | Render | Google Cloud | Fly.io | AWS Free Tier |
|----------|---------|--------|--------------|--------|--------------|
| **Cost** | $5/mo | FREE (750h) | FREE+$10DB | FREE (3 VMs) | FREE (1yr) |
| **Setup Time** | 15 min | 20 min | 30 min | 20 min | 45 min |
| **Learning Curve** | Very Easy | Easy | Medium | Medium | Medium |
| **Scalability** | Good | Limited | Excellent | Good | Good |
| **Both Services** | ✅ | ✅ Limited | ✅ | ✅ | ✅ |
| **Database** | ✅ Free | ✅ Free | $10/mo | ✅ Free | ✅ Free |
| **Support** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ |
| **⭐ BEST** | ✅ YES | Good alt | Production | Good alt | Only 1yr free |

---

### RECOMMENDATION FOR FREE TIER

**🎯 Use Railway.app**

**Why?**
- Simplest setup (no Docker knowledge needed)
- Auto-deploys from GitHub (push = deploy)
- Generous free tier ($5/month = ~light production load)
- Doubles as development environment
- Easy to upgrade when traffic grows
- Can use custom domain for $5

**Timeline**:
- **15 minutes**: Connect GitHub + Deploy both services
- **5 minutes**: Set environment variables
- **10 minutes**: Test deployment
- **Total: 30 minutes to production**

**Cost**: 
- **First month**: $5 free credit (Railway signup bonus)
- **After**: $5/month ongoing credit (covers most usage)
- **Total**: Essentially **FREE with $5/month allowance**

---

## 4.1 COMPREHENSIVE RAILWAY DEPLOYMENT GUIDE

### Prerequisites (5 minutes)
- GitHub account with your backend-servicex code
- Railway.app account (free signup at railway.app)
- Google OAuth2 credentials (for authentication)
- Groq API key (for AI service)
- AWS S3 bucket (for image storage) - optional for MVP
- That's it! No Docker knowledge required!

### Step-by-Step Deployment (20 minutes total)

#### Step 1️⃣: Create Railway Account & Connect GitHub (3 min)
```bash
# 1. Go to https://railway.app
# 2. Click "Start New Project" 
# 3. Select "Deploy from GitHub Repo"
# 4. Authorize Railway to access your GitHub account
# 5. Select "backend-servicex" repository
# 6. Click "Deploy Now"
```

**Result**: Railway creates a new project and auto-detects Maven

#### Step 2️⃣: Configure Spring Boot Service (5 min)
After deployment starts, you'll see the Spring Boot service building. While it builds:

```bash
# Click on "Spring Boot Service" → Variables tab
# Add these environment variables:

# Database (Railway provides automatic PostgreSQL connection)
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_JPA_SHOW_SQL=false

# Google OAuth2 - Get from Google Cloud Console
GOOGLE_CLIENT_ID=your_google_oauth_client_id_here
GOOGLE_CLIENT_SECRET=your_google_oauth_client_secret_here

# AWS S3 - Get from AWS IAM
AWS_ACCESS_KEY_ID=your_aws_access_key
AWS_SECRET_ACCESS_KEY=your_aws_secret_access_key
AWS_REGION=eu-north-1
S3_BUCKET_NAME=your-s3-bucket-name

# Gmail SMTP - For OTP emails
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_email@gmail.com
SPRING_MAIL_PASSWORD=your_gmail_app_password  # NOT your regular Gmail password!

# App Configuration
APP_PUBLICBASEURL=https://[railway-will-show-this-url]

# Logging
LOGGING_LEVEL_ROOT=INFO
```

**Important**: Don't close this—Railway shows your deployment URL here!

#### Step 3️⃣: Add PostgreSQL Database (2 min)
```bash
# In Railway Dashboard:
# 1. Click "+" icon (New Service)
# 2. Select "Database" → "PostgreSQL"
# 3. Click "Create"

# Railway automatically connects PostgreSQL to Spring Boot!
# You'll see a "DATABASE_URL" variable appear in Spring Boot's variables

# Verify in Spring Boot Variables:
# DATABASE_URL = postgresql://user:password@host:port/railway
```

**Result**: PostgreSQL is now running and connected!

#### Step 4️⃣: Deploy Python AI Service (5 min)
```bash
# Back in Railway Dashboard:
# 1. Click "New Service" → "GitHub Repo" → Select backend-servicex again
# 2. Click "Configure"
# 3. Set these fields:

Root Directory: src/main/ai-service-python
Build Command: pip install -r requirements.txt
Start Command: uvicorn app.main:app --host 0.0.0.0 --port $PORT

# 4. Click "Deploy"

# 5. Add environment variables for Python service:
#    Click on Python Service → Variables tab

GROQ_API_KEY=your_groq_api_key_from_groq.com
SPRING_BOOT_BASE_URL=https://[your-spring-boot-railway-url]
AI_MODEL=llama-4-scout
```

**Result**: Python FastAPI is now running!

#### Step 5️⃣: Update Spring Boot to Find Python Service (2 min)

Your Python service is now running at a different URL. Update the code:

```bash
# Edit: src/main/java/com/restaurant/admin/service/AiServiceClient.java

# Find this line:
private static final String BASE_URL = "http://localhost:8001";

# Replace with (get URL from Railway Python service dashboard):
private static final String BASE_URL = System.getenv("AI_SERVICE_URL");

# Add to Spring Boot variables in Railway:
AI_SERVICE_URL=https://[your-python-railway-url]

# Commit and push:
git add .
git commit -m "Update AI service URL for Railway deployment"
git push

# Railway auto-redeploys on push!
```

#### Step 6️⃣: Test Everything (3 min)
```bash
# Get your Spring Boot URL from Railway Dashboard
# Open in browser: https://[your-spring-boot-url]

# Test 1: Register
# Click login → Sign up
# Fill form → Should get OTP email

# Test 2: Create Menu Item
# Create restaurant → Add menu item with description

# Test 3: Get AI Suggestion
# Click "Add AI Suggestion" button
# Should see category suggestion within 5 seconds

# Test 4: Check Logs
# In Railway: Click service → Click "Logs" tab
# Should see requests coming in (no red errors)
```

**✅ You're LIVE!**

---

### 🔧 Troubleshooting Railway Deployment

#### Problem: Spring Boot shows "Build failed"
```bash
# Solution:
# 1. Click on Spring Boot service → Logs
# 2. Look for error message
# 3. Common fix: Missing Java version

# Add to pom.xml:
<properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
</properties>

# Push to GitHub → Railway redeploys
```

#### Problem: "Database connection refused"
```bash
# Solution:
# 1. Check PostgreSQL service is running
# 2. In Railway: Verify PostgreSQL shows "Running" (green dot)
# 3. Get connection string: 
#    Postgres Service → Variables tab → DATABASE_URL
# 4. Check Spring Boot has DATABASE_URL variable set
# 5. Restart Spring Boot service
```

#### Problem: "AI Service returned 503"
```bash
# Solution:
# 1. Check Python service is running (should be GREEN)
# 2. Verify GROQ_API_KEY is set correctly
# 3. Check Python service has SPRING_BOOT_BASE_URL pointing to correct Spring URL
# 4. Check logs: Python service → Logs tab
# 5. Restart if needed
```

#### Problem: S3 images not loading
```bash
# Solution (Option A - if using S3):
# 1. Verify AWS credentials in Spring Boot variables
# 2. Test S3 access: In any terminal:
#    aws s3 ls s3://your-bucket/ --profile default
# 3. Check S3 bucket policy allows public read

# Solution (Option B - simpler for MVP):
# Just skip S3 for now, use local uploads
# Images can be stored in database as Base64 temporarily
```

#### Problem: Google OAuth not working
```bash
# Solution:
# 1. Go to Google Cloud Console
# 2. Check OAuth2 Redirect URI includes your Railway URL:
#    https://[your-railway-url]/login/oauth2/code/google
# 3. Verify GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET in Railway
# 4. Restart Spring Boot service
```

---

### 📊 Monitoring Your Free Deployment

**View Dashboard Stats**:
```bash
# Railway → Project Dashboard
# Shows:
# ├─ CPU usage (should be < 50%)
# ├─ Memory usage (should be < 400MB of 512MB)
# ├─ Network I/O
# └─ Build history
```

**Check Logs**:
```bash
# Click any service → Logs tab
# Search for error messages or exceptions
# Most common: timeout, connection refused, OOM
```

**Set Alerts** (in Railway):
```bash
# Settings → Alerts
# Configure to email when:
# ├─ Service goes down
# ├─ Build fails
# └─ Resource limits exceeded
```

---

### 🚀 When to Upgrade from Free

**Stay on Free Tier if**:
- < 100 menu items
- < 10 restaurants
- < 100 QR scans/day
- Testing/MVP phase

**Upgrade to Paid ($5-15/month) when**:
- 512MB RAM insufficient (Python LLM needs > 1GB)
- Cold starts (30-60s) too slow
- Traffic requires faster response

**Upgrade Path**:
```
Railway Free Tier ($5 free credit)
      ↓ (traffic grows)
Railway Paid ($0.50/hour = $15-30/month)
      ↓ (massive scale)
AWS ECS/Kubernetes ($50-300+/month)
```

---

### 🎯 Next Steps (After Going Live)

#### Week 1: Monitor & Stabilize
- [ ] Check logs daily for errors
- [ ] Monitor response times
- [ ] Test user workflows

#### Week 2: Optimize
- [ ] Add caching for frequently requested menus
- [ ] Optimize database queries
- [ ] Consider Redis if needed

#### Week 3: Scale
- [ ] Upgrade RAM if cold starts too slow
- [ ] Set up auto-scaling
- [ ] Add monitoring alerts

---

### 💡 Pro Tips for Railway

**1. Environment Secrets**:
```bash
# Use Railway's "Private Variables" for sensitive data
# Don't commit API keys to GitHub!
# Railway: Variables → Toggle "Private" for API keys
```

**2. Quick Rollback**:
```bash
# If deployment breaks:
# Railway → Deployments tab → Click previous version → "Start"
# Rollback is instant!
```

**3. Custom Domain** (optional):
```bash
# Railway → Networking → Add Custom Domain
# Connect your own domain (e.g., api.myrestaurant.com)
# Costs: $5 for SSL cert setup (included in $5/mo)
```

**4. GitHub Auto-Deploy**:
```bash
# Push to GitHub → Railway auto-detects → Auto-builds → Auto-deploys
# No manual steps needed!
```

**5. View Database Directly**:
```bash
# Railway → PostgreSQL service → Click "Connect"
# Shows connection string to connect from any SQL client
# Can format database, run migrations, etc.
```

---

## 5. DEPLOYMENT CHECKLIST

### Pre-Deployment

- [ ] **Database**
  - [ ] PostgreSQL instance provisioned (AWS RDS or equivalent)
  - [ ] Database created and initialized
  - [ ] Backups configured
  - [ ] Connection pooling configured

- [ ] **External Services**
  - [ ] AWS S3 bucket created and configured
  - [ ] S3 IAM credentials generated
  - [ ] Google OAuth2 credentials obtained
  - [ ] Gmail SMTP credentials configured
  - [ ] Groq API key obtained

- [ ] **Environment Variables**
  - [ ] All credentials in secure vault (AWS Secrets Manager / Azure Key Vault)
  - [ ] Database connection strings verified
  - [ ] API endpoints verified
  - [ ] Logging levels appropriate for environment

- [ ] **Code**
  - [ ] All tests passing
  - [ ] Production builds tested locally
  - [ ] Security scanning performed
  - [ ] Dependencies audited

### Deployment

- [ ] **Runtime Validation**
  - [ ] Spring Boot health check: `GET /health`
  - [ ] Python FastAPI health check: `GET /health`
  - [ ] Database connectivity verified
  - [ ] S3 access confirmed
  - [ ] AI service communication working

- [ ] **Smoke Tests**
  - [ ] User can register
  - [ ] OAuth2 login works
  - [ ] Menu item creation works
  - [ ] AI categorization responds
  - [ ] QR code generation works

### Post-Deployment

- [ ] Monitoring enabled (logging, metrics, alerts)
- [ ] Backups verified
- [ ] Incident response procedures documented
- [ ] Team trained on deployment
- [ ] Rollback procedures tested

---

## 6. RECOMMENDED DEPLOYMENT STRATEGY

### For MVP / Initial Launch
**Use Option B (Docker) on AWS:**

1. **Build Phase**
   - Dockerize both Spring Boot and Python services
   - Create docker-compose.yml for local testing
   - Store images in AWS ECR

2. **Infrastructure Phase** (via AWS Console or Terraform)
   - Provision AWS RDS PostgreSQL
   - Create S3 bucket for images
   - Set up security groups and VPC

3. **Deployment Phase**
   - Deploy via AWS App Runner (easiest) or ECS Fargate
   - Configure auto-scaling policies
   - Set up CloudWatch monitoring

4. **CI/CD Phase** (later)
   - Set up GitHub Actions or AWS CodePipeline
   - Auto-build on git push
   - Auto-test and deploy

#### Estimated AWS Costs (Monthly)
- RDS PostgreSQL (db.t3.micro): **$35**
- S3 storage (100GB): **$2.30**
- App Runner or Fargate (light load): **$50-100**
- **Total: ~$90-140/month** for MVP

### For Production (Scale Phase)
**Upgrade to Option C (Kubernetes):**

1. Set up EKS (AWS Elastic Kubernetes Service)
2. Migrate services to K8s manifests
3. Configure Helm charts for versioning
4. Set up GitOps (ArgoCD) for deployments
5. Enable multi-region failover

---

## 7. TROUBLESHOOTING DEPLOYMENT ISSUES

### Issue: Spring Boot fails to start
**Check**:
```bash
# 1. Java version
java -version  # Should be 21+

# 2. Database connectivity
ping <RDS_HOST>

# 3. Environment variables
echo $SPRING_DATASOURCE_URL

# 4. Logs
docker logs <container_id>
```

### Issue: AI Service not responding
**Check**:
```bash
# 1. Health endpoint
curl http://localhost:8001/health

# 2. GROQ_API_KEY is set
echo $GROQ_API_KEY

# 3. Python dependencies installed
python -c "import langchain_groq; print('OK')"

# 4. Spring Boot can reach it
curl -X GET http://localhost:8081/api/ai/health
```

### Issue: S3 images not loading
**Check**:
```bash
# 1. AWS credentials
aws s3 ls

# 2. Bucket exists
aws s3 ls s3://your-bucket/

# 3. Bucket policy allows public read (or CloudFront configured)
```

---

## 8. QUICK START COMMANDS

### Local Development
```bash
# Terminal 1: PostgreSQL (via Docker)
docker run --name postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:15

# Terminal 2: Spring Boot
mvn spring-boot:run

# Terminal 3: Python AI Service
cd src/main/ai-service-python
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

### Docker Deployment
```bash
cd /home/mohdkat/projects/backend-servicex

# Build images
docker build -t flavorframe-backend .
docker build -t flavorframe-ai-service ./src/main/ai-service-python

# Run with compose
docker-compose up -d

# View logs
docker-compose logs -f backend
docker-compose logs -f ai-service
```

### AWS Deployment (ECS Fargate)
```bash
# 1. Push to ECR
aws ecr get-login-password --region eu-north-1 | docker login \
  --username AWS --password-stdin YOUR_ACCOUNT.dkr.ecr.eu-north-1.amazonaws.com

docker tag flavorframe-backend:latest YOUR_ACCOUNT.dkr.ecr.eu-north-1.amazonaws.com/flavorframe-backend:latest
docker push YOUR_ACCOUNT.dkr.ecr.eu-north-1.amazonaws.com/flavorframe-backend:latest

# 2. Deploy via AWS Console → ECS → Create Service → Select ECR image
```

---

## 9. KEY CONFIGURATIONS BY ENVIRONMENT

### Development (local)
```properties
# application-dev.properties
spring.jpa.hibernate.ddl-auto=create-drop
spring.h2.console.enabled=true
logging.level.root=DEBUG
```

### Staging
```properties
# application-staging.properties
spring.jpa.hibernate.ddl-auto=validate
logging.level.root=INFO
spring.datasource.hikari.maximum-pool-size=10
```

### Production
```properties
# application-prod.properties
spring.jpa.hibernate.ddl-auto=validate
logging.level.root=WARN
spring.datasource.hikari.maximum-pool-size=20
# Enable monitoring, caching, compression
```

---

## 10. MONITORING & OPERATIONS

### Health Checks
```bash
# Spring Boot
curl http://localhost:8081/health

# Python AI Service
curl http://localhost:8001/health

# Database
psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c "SELECT 1;"
```

### Logging
- **Spring Boot**: Logs to console and files
- **Python**: Structured logging via FastAPI
- **Recommendation**: Aggregate to CloudWatch, ELK Stack, or Datadog

### Metrics
- CPU/Memory usage
- Request latency (Spring Boot + Python)
- Database query times
- S3 upload/download times
- AI service response times

### Alerts
- Service health check failure
- Database connection issues
- S3 access errors
- API response time > 5 seconds
- Disk space critical

---

## 11. MIGRATION NOTES

### Current State (March 2026)
- ✅ Spring Boot 4.0.2 backend operational
- ✅ Python FastAPI AI service integrated
- ✅ Menu item categorization working
- ✅ S3 image storage active
- ✅ Gmail SMTP configured
- ⚠️ Still running on local development setup with ngrok

### Deployment Next Steps
1. **Week 1**: Containerize both services (Docker)
2. **Week 2**: Set up AWS infrastructure (RDS, S3, ECR)
3. **Week 3**: Deploy to AWS App Runner or ECS Fargate
4. **Week 4**: Configure CI/CD pipeline, monitoring, backups

---

## 12. QUESTIONS ANSWERED

### Q: Should we use Spring Boot vs Node.js?
**A**: Spring Boot is the right choice here because:
- ORM (JPA/Hibernate) handles complex database relationships
- Security framework is mature and battle-tested
- OAuth2 integration is straightforward
- Admin dashboard rendering via Thymeleaf works well

### Q: Do we need Kubernetes for MVP?
**A**: No. Docker + AWS App Runner / ECS Fargate is simpler and more cost-effective. Migrate to K8s only when you have:
- 10+ servers required
- Need for multi-region deployment
- Dedicated DevOps team

### Q: Should we split frontend into React SPA?
**A**: Recommended for future (Phase 2), but Thymeleaf works for MVP because:
- Server-side rendering simplifies deployment
- Admin dashboard doesn't need real-time interactivity
- Reduces infrastructure complexity (no Node.js build step)
- Migration path: Thymeleaf → React SPA is straightforward

### Q: How do we handle AI service failures?
**A**: Implemented with fallback pattern:
1. Spring Boot calls Python service
2. If Python timeout (> 5s), catch exception
3. Return cached previous suggestion or "Service temporarily unavailable"
4. Log error for monitoring
5. Circuit breaker pattern can be added later

### Q: Is our database design scalable?
**A**: Yes, but improvements for scale (later):
- **Indexing**: Add indexes on frequently queried columns (restaurant_id, user_id)
- **Partitioning**: Partition menu_items by restaurant_id if > 10M rows
- **Read Replicas**: Add RDS read replicas for reporting
- **Caching**: Add Redis for frequently requested menus

---

## CONCLUSION

**FlavorFrame Backend-ServiceX** is ready for deployment. The recommended path is:

1. **Use Docker** (Option B) for consistent environments
2. **Deploy to AWS** (App Runner or ECS Fargate) for minimal operational overhead
3. **Monitor with CloudWatch** for observability
4. **Plan migration to K8s** (Option C) when traffic requires auto-scaling

**Estimated time to production: 2-3 weeks**

---

**For questions or clarifications, refer to:**
- `/home/mohdkat/projects/backend-servicex/AI_INTEGRATION_GUIDE.md` - AI service details
- `/home/mohdkat/projects/backend-servicex/.env` - Environment setup
- Spring Boot Docs: https://docs.spring.io/spring-boot/docs/4.0.2/reference/html/
- FastAPI Docs: https://fastapi.tiangolo.com/
