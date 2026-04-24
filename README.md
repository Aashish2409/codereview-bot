# 🤖 CodeReview Bot

An AI-powered GitHub Pull Request review bot built with **Spring Boot** and **Groq LLaMA 3**.  
When a PR is opened, the bot automatically fetches the diff, sends it to an AI model, and posts a detailed code review as a GitHub comment.

---

## ✨ Features

- **Automatic PR reviews** via GitHub Webhooks
- **AI-powered feedback** using Groq's LLaMA 3 (70B)
- **Security hardened**: HMAC-SHA256 signature verification, prompt injection guards, rate limiting
- **Dashboard API** for frontend visualization of review history
- **One-click deployment** to Railway via Docker

---

## 🔐 Security Implementations

| Threat | Defence |
|---|---|
| Forged webhooks | HMAC-SHA256 signature verification (constant-time compare) |
| Prompt injection via code | Delimited input + explicit system prompt guard |
| API key exposure | Environment variables only — never in source code |
| DoS via huge diffs | Hard cap at 8000 chars before sending to AI |
| Rate abuse | Bucket4j per-repository rate limiter |
| Error info leakage | GlobalExceptionHandler — no stack traces in responses |
| Over-permissioned token | Fine-grained GitHub PAT with only `pull_requests:write` |

---

## 🚀 Quick Start

### 1. Clone and configure environment variables

```bash
git clone https://github.com/YOUR_USERNAME/codereview-bot
cd codereview-bot
```

Create a `.env` file (never commit this):
```env
GITHUB_TOKEN=github_pat_xxxx
GITHUB_WEBHOOK_SECRET=your_random_secret_here
GROQ_API_KEY=gsk_xxxx
```

### 2. Run locally

```bash
# Export env vars
export GITHUB_TOKEN=github_pat_xxxx
export GITHUB_WEBHOOK_SECRET=your_secret
export GROQ_API_KEY=gsk_xxxx

# Run
mvn spring-boot:run
```

### 3. Expose localhost with ngrok

```bash
ngrok http 8080
# Copy the https URL e.g. https://abc123.ngrok.io
```

### 4. Configure GitHub Webhook

- Go to your repo → **Settings → Webhooks → Add webhook**
- Payload URL: `https://abc123.ngrok.io/webhook`
- Content type: `application/json`
- Secret: same value as `GITHUB_WEBHOOK_SECRET`
- Events: **Pull requests** only

### 5. Test it!

Open a Pull Request on your repo — the bot will comment within ~10 seconds.

---

## 🌐 API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/webhook` | GitHub webhook receiver |
| GET | `/api/reviews` | All recent review records (JSON) |
| GET | `/api/stats` | Summary statistics |
| GET | `/api/health` | Health check |

---

## ☁️ Deploy to Railway

```bash
# Build
mvn clean package -DskipTests

# Push to GitHub, then on Railway:
# New Project → Deploy from GitHub → Select repo
# Add env vars: GITHUB_TOKEN, GITHUB_WEBHOOK_SECRET, GROQ_API_KEY
# Update your GitHub webhook URL to the Railway URL
```

---

## 🏗️ Project Structure

```
src/main/java/com/codereviewbot/
├── controller/
│   ├── WebhookController.java      ← GitHub webhook entry point
│   └── DashboardController.java    ← REST API for frontend
├── service/
│   ├── GitHubService.java          ← Fetch diff, post comment
│   ├── AIReviewService.java        ← Groq/LLaMA integration
│   ├── RateLimiterService.java     ← Per-repo rate limiting
│   └── ReviewLogService.java       ← In-memory review log
├── security/
│   └── WebhookSignatureVerifier.java ← HMAC-SHA256 verification
├── model/
│   ├── WebhookPayload.java         ← GitHub payload model
│   └── ReviewRecord.java           ← Review log entry model
├── config/
│   ├── AppConfig.java              ← WebClient, CORS, ObjectMapper
│   └── GlobalExceptionHandler.java ← Clean error responses
└── util/
    └── PromptBuilder.java          ← Safe AI prompt construction
```

---

## 🛠️ Tech Stack

- **Java 17** + **Spring Boot 3.2**
- **Spring WebFlux** (WebClient for async HTTP)
- **Bucket4j** (token-bucket rate limiting)
- **Groq API** (LLaMA 3 70B)
- **GitHub REST API v3**
- **Docker** + **Railway** (deployment)
