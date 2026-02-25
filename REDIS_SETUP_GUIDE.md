# Redis Setup Guide for RideApp

## Problem
The application is trying to connect to Redis at `localhost:6379` but Redis is not running or not accessible.

## Configuration Details
Your application expects Redis at:
- **Host:** localhost
- **Port:** 6379

(See `src/main/resources/application.yaml`)

## Solutions

### Option 1: Start Redis Server (Windows)

#### 1a. Using Windows Subsystem for Linux (WSL) - Recommended
```bash
# Open PowerShell as Administrator and enable WSL
wsl --install

# Then in WSL terminal:
sudo apt update
sudo apt install redis-server
sudo service redis-server start

# Verify Redis is running:
redis-cli ping
# Should return: PONG
```

#### 1b. Using Docker - Easiest
```bash
# Install Docker Desktop for Windows (if not already installed)
# Then run:
docker run -d -p 6379:6379 --name redis redis:latest

# Verify it's running:
docker exec redis redis-cli ping
# Should return: PONG

# To stop Redis later:
docker stop redis
docker rm redis
```

#### 1c. Using Windows Redis Build (Alternative)
```bash
# Download Redis for Windows from:
# https://github.com/microsoftarchive/redis/releases

# Extract and run in PowerShell:
cd path\to\redis
.\redis-server.exe
```

#### 1d. Using Chocolatey
```powershell
# Install Chocolatey if not installed, then:
choco install redis-64 -y

# Start Redis:
redis-server

# In another terminal, verify:
redis-cli ping
# Should return: PONG
```

### Option 2: Change Application Configuration (Temporary)

If you don't want to use Redis yet, you can disable it in the application:

Edit `src/main/resources/application.yaml`:
```yaml
spring:
  data:
    redis:
      # host: localhost    # Comment out
      # port: 6379          # Comment out
```

However, the ride dispatch feature requires Redis for:
- Idempotency tracking
- Driver geolocation tracking
- Caching

So this is **not recommended** for production.

---

## Testing Redis Connection

Once Redis is running, you can verify the connection:

```bash
# From PowerShell/CMD:
redis-cli ping
# Expected output: PONG

redis-cli
# Opens interactive Redis terminal
# Type: COMMAND
# Should list available commands
```

---

## Application Logs

After starting Redis, check the application logs for:
```
[main] ... o.s.d.r.c.RedisConnectionFactory : Connecting to Redis at localhost:6379
[main] ... o.s.d.r.c.RedisConnectionFactory : Connection OK
```

If you see:
```
org.springframework.data.redis.connection.RedisConnectionFailureException: Unable to connect to Redis
```

Redis is still not running or not accessible. Verify:
1. Redis is running: `redis-cli ping`
2. Port 6379 is not blocked by firewall
3. No other application is using port 6379 (run `netstat -ano | findstr :6379`)

---

## Quick Test After Redis is Running

Test the ride request endpoint with sample data:

```bash
curl -X POST http://localhost:8081/api/dispatch/ride/request \
  -H "Content-Type: application/json" \
  -d '{
    "riderId": "550e8400-e29b-41d4-a716-446655440000",
    "pickupLat": 12.9716,
    "pickupLong": 77.5946,
    "destLat": 13.0827,
    "destLong": 78.4867,
    "tier": "economy",
    "paymentMethod": "upi",
    "requestId": "req-abc123xyz-2025"
  }'
```

Expected response (if no drivers available):
```json
{
  "error": "Conflict",
  "message": "No drivers nearby"
}
```

This is expected since you probably don't have driver data yet.

---

## Recommended: Use Docker for Local Development

Docker is the quickest and cleanest approach:

```bash
# Start Redis in background
docker run -d -p 6379:6379 --name rideapp-redis redis:latest

# Run your Spring Boot app in IDE or terminal
mvn spring-boot:run

# When done, stop Redis
docker stop rideapp-redis
```

---

## Still Having Issues?

Check:
1. **Application Logs** - Look for Redis connection errors
2. **Firewall** - Ensure port 6379 is not blocked
3. **Port Conflict** - Run `netstat -ano | findstr :6379` to see if another process uses it
4. **Redis Version** - Ensure you're using Redis 6.0+

---

## Next Steps

1. Start Redis using one of the methods above
2. Restart your Spring Boot application
3. The "Unable to connect to Redis" error should disappear
4. You can then test the ride request endpoint

