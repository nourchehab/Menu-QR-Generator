# FREE Deployment to Railway - Quick Reference

**Status**: ✅ **RECOMMENDED FOR FREE DEPLOYMENT**  
**Cost**: FREE ($5/month free credit usually covers everything)  
**Time**: 30 minutes to production  
**Effort**: Very Easy (no Docker knowledge needed!)

---

## 🎯 Why Railway is Best for Free

✅ Supports Spring Boot + Python together  
✅ Free PostgreSQL database included  
✅ $5/month free credit (signup bonus)  
✅ Auto-deploys from GitHub (push = live)  
✅ No credit card required for free tier  
✅ Can upgrade later when traffic grows  

---

## ⚡ 30-Second Deploy Summary

```bash
# 1. Go to railway.app
# 2. Connect GitHub → Select backend-servicex
# 3. Click "Deploy" 
# 4. Wait 2 minutes (builds automatically)
# 5. Add PostgreSQL (click "+" → Database)
# 6. Deploy Python service (same GitHub repo, different folder)
# 7. Set environment variables
# 8. Done! 

# Total: 30 minutes
```

---

## 📋 Complete Walkthrough

👉 **See [FREE_DEPLOYMENT_RAILWAY.md](FREE_DEPLOYMENT_RAILWAY.md) for detailed steps**

Or follow the comprehensive guide: Section 4.1 in [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#41-comprehensive-railway-deployment-guide)

---

## 💰 Cost Comparison

| Provider | Cost | Setup Time | Both Services |
|----------|------|-----------|--------------|
| **Railway** ⭐ | FREE | 30 min | ✅ YES |
| Render | Free/750h | 20 min | ⚠️ Limited |
| Google Cloud Run | FREE+$10DB | 45 min | ✅ YES |
| Fly.io | FREE (3 VMs) | 30 min | ✅ YES |
| AWS Free Tier | FREE | 60 min | ✅ 1 year only |
| Vercel | FREE | N/A | ❌ Not suitable |

---

## 🚀 Get Started Now

**For absolute quickest path**: [FREE_DEPLOYMENT_RAILWAY.md](FREE_DEPLOYMENT_RAILWAY.md)

**For comprehensive guide**: [DEPLOYMENT_GUIDE.md - Section 4.1](DEPLOYMENT_GUIDE.md)

**Questions?** See Troubleshooting section in guides

---



**What You Have**:
- ✅ Spring Boot backend (REST API + Thymeleaf templates)
- ✅ Python FastAPI AI service (menu categorization with Groq Llama)
- ✅ PostgreSQL database (AWS RDS ready)
- ✅ AWS S3 integration (image storage)
- ✅ Google OAuth2 authentication
- ✅ QR code generation
- ⚠️ Currently on ngrok tunneling + local development

**Time to Production**: 2-3 weeks  
**Estimated Cost**: $100-300/month (depending on traffic)  
**Risk Level**: Low (all components tested)

---

## Deployment Options Ranked

### 1. ⭐ RECOMMENDED: Docker + AWS App Runner

**Why This?**
- Easiest production setup
- Fast deployment (< 1 day)
- Auto-scaling built-in
- Pay only for what you use
- Zero DevOps complexity for MVP

**What You Do**:
```bash
# 1. Containerize services
docker build -t backend .
docker build -t ai-service ./src/main/ai-service-python

# 2. Push to AWS ECR
aws ecr push ...

# 3. Deploy via AWS Console (App Runner)
# Click → Create Service → Select ECR image → Deploy
# Done!
```

**Cost**:
- AWS App Runner (2 services): $50-100/month
- RDS PostgreSQL (db.t3.micro): $35/month
- S3 storage (100GB): $2.30/month
- **Total**: ~$90-140/month

**Setup Time**: 1-2 days  
**Operations**: Minimal (AWS handles most)

**For**: MVP, small teams, rapid deployment

---

### 2. Traditional Servers (VPS on EC2/DigitalOcean)

**What You Do**:
```bash
# 1. Rent EC2 instance (t3.medium)
# 2. SSH in and install Java, Python, PostgreSQL
# 3. Clone repo, build, run
java -jar backend.jar &
uvicorn app.main:app &

# 4. Configure Nginx as reverse proxy
# 5. Set up SSL with Let's Encrypt
```

**Cost**:
- EC2 t3.medium: $35/month
- RDS PostgreSQL: $35/month
- **Total**: ~$70-100/month (cheapest)

**Setup Time**: 3-5 days  
**Operations**: Moderate (manual updates, monitoring)

**For**: Budget-conscious, existing AWS users

---

### 3. Kubernetes (EKS)

**What You Do**:
```bash
# 1. Create EKS cluster
# 2. Write Kubernetes manifests
# 3. Deploy via kubectl
# 4. Configure auto-scaling, monitoring, etc.
```

**Cost**:
- EKS cluster: $70/month
- Compute nodes: $50-200/month+
- **Total**: $120-300/month (overkill for MVP)

**Setup Time**: 2-4 weeks  
**Operations**: High (requires DevOps expertise)

**For**: Enterprise, multi-region, high availability

---

## My Recommendation (In Priority Order)

### Phase 1 (Current) - MVP Launch
**Use: Docker + AWS App Runner**

```
Week 1: Containerize
├─ Create Dockerfile for Spring Boot
├─ Create Dockerfile for Python
├─ Test with docker-compose locally
└─ Commit to git

Week 2: Deploy to AWS
├─ Create AWS ECR repositories
├─ Push Docker images
├─ Deploy via App Runner
├─ Configure database connection
└─ Run smoke tests

Week 3: Go Live
├─ Set up CloudWatch monitoring
├─ Configure alerting
├─ Document procedures
└─ Launch!

**Expected Cost**: $100-150/month
**Time to Live**: 2 weeks
```

### Phase 2 (6 months later, after customer validation)
**Upgrade Path: Add CI/CD**

```
├─ GitHub Actions → Auto-build on push
├─ Auto-run tests
├─ Auto-push to ECR
├─ Auto-deploy to App Runner
└─ Zero-downtime deployments

**Benefit**: Deployment automation, safer releases
```

### Phase 3 (if massive growth)
**Upgrade Path: Kubernetes**

```
├─ Migrate to EKS or managed K8s
├─ Multi-region deployment
├─ Advanced auto-scaling
└─ Global load balancing

**Trigger**: > 1000 monthly active users OR > 10M menu items
```

---

## What NOT to Do

❌ **Don't use serverless (Lambda) for now**
- Backend is stateful (sessions, fileI/O)
- Lambda has cold start issues (5+ seconds)
- Doesn't fit your architecture

❌ **Don't go straight to Kubernetes**
- Premature complexity
- Requires dedicated DevOps person
- Easier to migrate TO it later than FROM it

❌ **Don't skip monitoring**
- Set up CloudWatch NOW
- Monitor: CPU, memory, errors, latency
- Set alerts for failures

❌ **Don't hardcode config**
- Use environment variables
- Store secrets in AWS Secrets Manager
- Never commit .env files

---

## Quick Deployment Checklist

### Pre-Deployment (1 day)
- [ ] All tests passing (`mvn test`)
- [ ] No hardcoded credentials in code
- [ ] Environment variables list created
- [ ] Database migrations tested
- [ ] S3 bucket created and tested
- [ ] SSL certificate ready
- [ ] Monitoring dashboard set up

### Deployment (1 day)
- [ ] Dockerfiles created and tested locally
- [ ] Push images to AWS ECR
- [ ] RDS database provisioned
- [ ] Environment variables configured
- [ ] Deploy to App Runner
- [ ] Health endpoints responding
- [ ] Database connection verified
- [ ] S3 access verified

### Post-Deployment (1 day)
- [ ] Smoke tests pass (register user, create item, AI suggestion)
- [ ] Monitoring showing green
- [ ] Error logs clean
- [ ] Performance acceptable (< 2s response time)
- [ ] Backups configured
- [ ] Disaster recovery plan documented

---

## Cost Breakdown (Monthly)

### Minimal Setup ($90/month)
```
AWS App Runner (backend)        $30
AWS App Runner (ai-service)     $30
RDS PostgreSQL (t3.micro)       $35
S3 storage (100GB)              $2.30
CloudWatch logging              ~$5
─────────────────────────────────
TOTAL                           ~$102/month
```

### Growth Setup ($250/month)
```
AWS App Runner (scaled)         $60
AWS App Runner (ai-service)     $40
RDS PostgreSQL (t3.small)       $70
S3 storage (500GB)              $11.50
CloudWatch + monitoring         $20
DynamoDB cache (optional)       $30
─────────────────────────────────
TOTAL                           ~$231/month
```

### Enterprise Setup ($500/month)
```
EKS cluster                     $70
Compute nodes (3x)              $150
RDS PostgreSQL (t3.large)       $150
S3 storage (1TB)                $23
ElastiCache Redis               $50
CloudWatch + DataDog            $50
─────────────────────────────────
TOTAL                           ~$493/month
```

---

## Implementation Path

### Step 1: Containerize (1-2 days)

**Backend Dockerfile**:
```dockerfile
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests
FROM eclipse-temurin:21-jre
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
```

**Python Dockerfile**:
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY app app/
EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0"]
```

**Test Locally**:
```bash
docker-compose up
# Verify: http://localhost:8081 and http://localhost:8001
```

### Step 2: AWS Setup (1 day)

**Create Resources**:
```bash
# 1. ECR repositories
aws ecr create-repository --repository-name flavorframe-backend
aws ecr create-repository --repository-name flavorframe-ai-service

# 2. RDS PostgreSQL
aws rds create-db-instance \
  --db-instance-identifier flavorframe-db \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --master-username postgres \
  --master-user-password [STRONG-PASSWORD]

# 3. S3 bucket
aws s3 mb s3://flavorframe-images

# 4. ECR login & push
aws ecr get-login-password | docker login --username AWS --password-stdin [ACCOUNT].dkr.ecr.eu-north-1.amazonaws.com

docker tag backend [ACCOUNT].dkr.ecr.eu-north-1.amazonaws.com/flavorframe-backend
docker push [ACCOUNT].dkr.ecr.eu-north-1.amazonaws.com/flavorframe-backend
```

### Step 3: Deploy to App Runner (1 day)

**Via AWS Console**:
1. App Runner → Create Service
2. Source: Container registry (ECR)
3. Select image: `flavorframe-backend:latest`
4. Configure environment variables
5. Deploy

**Or via CLI**:
```bash
aws apprunner create-service \
  --service-name flavorframe-backend \
  --source-configuration ImageRepository={ImageIdentifier=[...]}
```

### Step 4: Verify (2 hours)

```bash
# 1. Check service health
curl https://[app-runner-url]/health

# 2. Test signup flow
# 3. Test menu item creation
# 4. Test AI categorization
# 5. Check CloudWatch logs for errors
```

---

## Monitoring Setup (Critical!)

### AWS CloudWatch Metrics to Monitor
```
├─ CPU utilization (alert if > 80%)
├─ Memory utilization (alert if > 85%)
├─ Request count
├─ Error rate (alert if > 1%)
├─ Response time (p95 < 2s)
└─ Database connections (alert if > 80% of pool)
```

### Application Logs
```
├─ Spring Boot errors → CloudWatch Logs
├─ Python FastAPI errors → CloudWatch Logs
├─ Database slow queries (log & alert)
├─ S3 access errors
└─ OAuth2 failures
```

### Alarms to Set Up
```
├─ Service down (health check failed)
├─ Error rate > 5%
├─ Response time > 5 seconds
├─ Database unreachable
├─ S3 access errors
├─ Out of database connections
└─ Low disk space
```

---

## Rollback Procedure

**If something breaks**:

### Option 1: Quick Rollback (< 5 minutes)
```bash
# Deploy previous Docker image version
aws apprunner update-service \
  --service-arn arn:aws:apprunner:... \
  --source-configuration ImageRepository={ImageIdentifier=[OLD-VERSION]}

# Or via App Runner console: Deployments → Select previous → Deploy
```

### Option 2: Database Rollback (if schema changed)
```bash
# Restore from RDS snapshot
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier flavorframe-db-restored \
  --db-snapshot-identifier snapshot-id

# Update connection string in env var
```

---

## Next Actions

### Immediate (This Week)
1. [ ] Review and approve this deployment plan
2. [ ] Create AWS account (if not done)
3. [ ] Set up IAM user for CI/CD
4. [ ] Create Dockerfiles (I can help)

### Week 1
1. [ ] Test Docker build locally
2. [ ] Create AWS resources (ECR, RDS, S3)
3. [ ] Set up environment variables

### Week 2
1. [ ] Deploy to App Runner
2. [ ] Run smoke tests
3. [ ] Set up monitoring & alerting

### Week 3
1. [ ] Go live!
2. [ ] Monitor for issues
3. [ ] Gather customer feedback

---

## Questions?

**Q: What if traffic spikes?**  
A: App Runner auto-scales. No action needed. Check costs in AWS console.

**Q: How do I update the code?**  
A: Push new Docker image to ECR → App Runner auto-deploys (you can configure this).

**Q: Can we deploy to multiple regions?**  
A: Yes, with App Runner, but not needed yet. Plan for later.

**Q: What if database connection fails?**  
A: Spring Boot will retry. If persistent, check RDS logs. CloudWatch alerts will notify you.

**Q: How do we handle secrets (passwords, API keys)?**  
A: Store in AWS Secrets Manager, not in code. App Runner can inject as environment variables.

**Q: Is the Python AI service critical?**  
A: No. Menu categorization is optional. If it fails, Spring Bot catches exception and shows "Service temporarily unavailable". Menu still works.

---

## Decision Required

### Choose One:

1. **🚀 Go with Recommendation** (Docker + App Runner)
   - Fastest to MVP
   - Lowest risk
   - Easy to grow

2. **💰 Save Money** (Traditional VPS)
   - Slightly cheaper
   - More manual work
   - OK for MVP

3. **🏢 Enterprise Ready** (Kubernetes)
   - Overkill for now
   - High setup cost
   - Better to add later when needed

**My Vote**: Option 1 ✅

---

**Status**: Ready for deployment  
**Approval Needed**: YES (from project stakeholder)  
**Estimated Timeline**: 2-3 weeks to production  
**Risk Level**: LOW (MVP already tested)
