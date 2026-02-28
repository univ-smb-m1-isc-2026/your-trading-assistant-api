# API Test Commands - Authentication Endpoints

This document provides curl commands to test the Trading Assistant API's authentication endpoints.

**Base URL:** `http://localhost:8080`

---

## 1. Register a New Account

### Command
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "Jean Dupont",
    "email": "jean@example.com",
    "password": "SecurePassword123"
  }'
```

### Response (Success - 200 OK)
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqZWFuQGV4YW1wbGUuY29tIiwiaWQiOjEsInVzZXJuYW1lIjoiSmVhbiBEdXBvbnQiLCJpYXQiOjE2OTEwMDAwMDAsImV4cCI6MTY5MTA4NjQwMH0.signature..."
}
```

### Validation Rules
- **username:** Required, non-empty string
- **email:** Required, valid email format
- **password:** Required, minimum 8 characters

### Error Cases
```bash
# Missing email (400 Bad Request)
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "Jean Dupont",
    "password": "SecurePassword123"
  }'

# Invalid email format (400 Bad Request)
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "Jean Dupont",
    "email": "invalid-email",
    "password": "SecurePassword123"
  }'

# Password too short (400 Bad Request)
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "Jean Dupont",
    "email": "jean@example.com",
    "password": "short"
  }'

# Email already registered (409 Conflict or 400 Bad Request)
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "Another Name",
    "email": "jean@example.com",
    "password": "SecurePassword123"
  }'
```

---

## 2. Login with Existing Account

### Command
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jean@example.com",
    "password": "SecurePassword123"
  }'
```

### Response (Success - 200 OK)
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqZWFuQGV4YW1wbGUuY29tIiwiaWQiOjEsInVzZXJuYW1lIjoiSmVhbiBEdXBvbnQiLCJpYXQiOjE2OTEwMDAwMDAsImV4cCI6MTY5MTA4NjQwMH0.signature..."
}
```

### Validation Rules
- **email:** Required, valid email format
- **password:** Required, non-empty string

### Error Cases
```bash
# Missing password (400 Bad Request)
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jean@example.com"
  }'

# Invalid email format (400 Bad Request)
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "not-an-email",
    "password": "SecurePassword123"
  }'

# Wrong password (401 Unauthorized or 400 Bad Request)
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jean@example.com",
    "password": "WrongPassword"
  }'

# Account does not exist (401 Unauthorized or 400 Bad Request)
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "nonexistent@example.com",
    "password": "SomePassword123"
  }'
```

---

## 3. Use JWT Token to Access Protected Endpoints

After successfully registering or logging in, you'll receive a JWT token. Use it to access protected endpoints:

```bash
# Store the token (replace with your actual token)
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Access a protected endpoint with the token
curl -X GET http://localhost:8080/protected-resource \
  -H "Authorization: Bearer $TOKEN"
```


## 7. Common Issues & Solutions

### Issue: CORS Error
**Problem:** Frontend running on different port gets CORS error
**Solution:** Configure CORS in `SecurityConfig.java`

### Issue: 405 Method Not Allowed
**Problem:** Using GET instead of POST
**Solution:** Use `curl -X POST` (or `curl --request POST`)

### Issue: 400 Bad Request with validation error
**Problem:** Email format is invalid or password is too short
**Solution:** Check the validation rules above; ensure password is at least 8 characters

### Issue: Token expired
**Problem:** JWT token was generated but more than 24 hours have passed
**Solution:** Generate a new token via login or register

-



---

**Last Updated:** 2026-02-28  
**API Version:** 0.0.1-SNAPSHOT
