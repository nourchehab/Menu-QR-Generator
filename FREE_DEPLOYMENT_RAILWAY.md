# FREE Deployment to Railway - Quick Reference

**Cost**: $5/month free credit (usually FREE for MVP)  
**Time**: 30 minutes to live  
**Difficulty**: Very Easy (no Docker needed)

---

## 🚀 30 Minute Quickstart

### 1. Go to railway.app → Start New Project
- Connect GitHub
- Select backend-servicex repo
- Click "Deploy"

### 2. Get Spring Boot URL
- Wait for build (2-3 minutes)
- Dashboard shows: `https://[project]-production.up.railway.app`
- Note this URL!

### 3. Add PostgreSQL
- Click "+" → Database → PostgreSQL
- Railway auto-connects it
- Done!

### 4. Set Environment Variables for Spring Boot
Click Spring Boot service → Variables → Add:

```
SPRING_JPA_HIBERNATE_DDL_AUTO=update
GOOGLE_CLIENT_ID=your_google_id
GOOGLE_CLIENT_SECRET=your_google_secret
AWS_ACCESS_KEY_ID=your_aws_key
AWS_SECRET_ACCESS_KEY=your_aws_secret
AWS_REGION=eu-north-1
S3_BUCKET_NAME=your_bucket
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_email@gmail.com
SPRING_MAIL_PASSWORD=gmail_app_password
```

### 5. Deploy Python AI Service
- New Service → GitHub Repo → backend-servicex → Configure
- Root Directory: `src/main/ai-service-python`
- Build Command: `pip install -r requirements.txt`
- Start Command: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`
- Variables:
  ```
  GROQ_API_KEY=your_groq_key
  SPRING_BOOT_BASE_URL=https://[spring-boot-url-from-step-2]
  AI_MODEL=llama-4-scout
  ```

### 6. Update Code for Python URL
Edit `src/main/java/com/restaurant/admin/service/AiServiceClient.java`:
```java
private static final String BASE_URL = System.getenv("AI_SERVICE_URL");
```

Add to Spring Boot variables:
```
AI_SERVICE_URL=https://[python-service-url-from-step-5]
```

Push to GitHub:
```bash
git add .
git commit -m "Update AI service URL"
git push
```

### 7. Test in Browser
- Open `https://[spring-boot-url]`
- Register account
- Create menu item
- Click "Get AI Suggestion"
- Should work!

---

## 🛠️ Environment Variables Cheat Sheet

### Spring Boot Variables (Required)
```
# Database (Railway provides this automatically)
DATABASE_URL=postgresql://... (auto-set)

# Auth
GOOGLE_CLIENT_ID=from Google Cloud Console
GOOGLE_CLIENT_SECRET=from Google Cloud Console

# AWS S3 (for images)
AWS_ACCESS_KEY_ID=from AWS IAM
AWS_SECRET_ACCESS_KEY=from AWS IAM
AWS_REGION=eu-north-1
S3_BUCKET_NAME=your-bucket-name

# Gmail (for OTP emails)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=yourEmail@gmail.com
SPRING_MAIL_PASSWORD=app_specific_password (NOT your Gmail password!)

# App Config
APP_PUBLICBASEURL=https://[railway-url]
SPRING_JPA_HIBERNATE_DDL_AUTO=update
```

### Python AI Service Variables (Required)
```
GROQ_API_KEY=from Groq console (https://console.groq.com)
SPRING_BOOT_BASE_URL=https://[your-spring-server-url]
AI_MODEL=llama-4-scout
```

---

## ✅ Verification Checklist

- [ ] Spring Boot service shows GREEN (running)
- [ ] PostgreSQL service shows GREEN
- [ ] Python service shows GREEN
- [ ] Can open Spring Boot URL in browser
- [ ] Can see login page
- [ ] Can register new account
- [ ] Can create menu item
- [ ] Can click "Get AI Suggestion" (wait 5 sec)
- [ ] AI returns category + confidence
- [ ] All logs are clean (no red errors)

---

## 🐛 Quick Fixes

### Service Won't Start
```bash
# View logs: Click service → Logs
# Most common: Missing Java version
# Add to pom.xml:
<properties>
  <java.version>21</java.version>
</properties>
# Push → Railway redeploys
```

### Database Connection Error
```bash
# Check PostgreSQL is running (GREEN)
# Copy DATABASE_URL from Postgres variables
# Paste into Spring Boot variables
# Restart Spring Boot
```

### AI Service Returns 503
```bash
# Check Python service is GREEN
# Verify GROQ_API_KEY is correct
# Check SPRING_BOOT_BASE_URL points to correct Spring URL
# View Python logs
```

### Gmail OTP Not Sending
```bash
# Use Gmail app-specific password (NOT Gmail password)
# Enable "Less secure app access" if needed
# Check Spring Boot logs for smtp error
```

---

## 📊 Monitoring

**View Dashboard**:
- CPU usage (should be < 50%)
- Memory usage (should be < 400MB of 512MB)
- Network (requests/responses)
- Logs (click service → Logs)

**Common Issues**:
- Memory > 500MB → Python service needs optimization
- Cold start > 60s → Normal on free tier, upgrade if too slow
- Errors in logs → Check environment variables

---

## 💰 Cost Breakdown

| Item | Cost | Notes |
|------|------|-------|
| Spring Boot compute | FREE | $5/month credit covers |
| Python compute | FREE | Included in credit |
| PostgreSQL | FREE | Included in credit |
| Bandwidth | FREE | No overage charges |
| SSL cert | FREE | Included |
| **Total** | **FREE** | ~$5/month allowance |

**Upgrade path**:
- Traffic grows → $0.50/hour compute (～$15+/month)
- Need better performance → Add second instance
- Global distribution → Upgrade to paid plan with auto-scaling

---

## 🎓 Learn More

- [Railway Documentation](https://railway.app/docs)
- [Spring Boot on Railway](https://railway.app/docs/guides/spring-boot)
- [FastAPI on Railway](https://railway.app/docs/guides/python)
- [PostgreSQL on Railway](https://railway.app/docs/guides/databases)

---

## ❓ FAQ

**Q: Is it really free?**  
A: Yes! First month has $5 credit. After that, ~$5/month allowance usually covers small deployments.

**Q: Will it go down?**  
A: Railway has 99.9% uptime SLA. Auto-restarts if crashes.

**Q: Can I scale to production?**  
A: Yes! Start free, pay as you grow. Easily upgrade to more instances/regions.

**Q: Can I use custom domain?**  
A: Yes, add domain in Railway for $5 one-time setup.

**Q: What if I exceed free tier?**  
A: Railway pauses deployment (won't charge). You can upgrade or optimize.

**Q: Can I rollback if something breaks?**  
A: Yes! Railway → Deployments → Select previous version → "Start"

**Q: How do I see logs?**  
A: Click service → Logs tab. Streams in real-time.

---

## ✨ Pro Tips

1. **Use Private Variables** for API keys (don't commit to GitHub)
2. **GitHub Auto-Deploy** - Push code → Railway auto-builds & deploys
3. **Rollback Instantly** if something breaks
4. **PostgreSQL Client** - Connect your own SQL client to view/edit database
5. **Set Alerts** - Get notified if service goes down

---

**Status**: Ready to deploy  
**Approval**: None needed (free tier!)  
**Timeline**: 30 minutes to production  
**Risk**: Very low (free trial, easy rollback)
