# Hostel Complaint & Maintenance System (HCMS) - REST API Documentation

**Base URL**: `/api/v1`  
**Authentication**: Bearer JWT in `Authorization` header (`Bearer <token>`) or `token` HttpOnly cookie.

All responses use standard HTTP status codes and uniform JSON envelopes.

---

## 1. Uniform Error Shape

When an error occurs, the server responds with a consistent JSON body and no leaked stack traces:

```json
{
  "timestamp": "2026-10-19T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Field 'roomNumber' is mandatory",
  "path": "/api/v1/auth/register",
  "details": []
}
```

### Standard Status Codes:
- `200 OK`: Request succeeded.
- `201 Created`: Resource successfully created.
- `400 Bad Request`: Malformed payload or failed validation.
- `401 Unauthorized`: Missing or invalid JWT token.
- `403 Forbidden`: Insufficient RBAC permissions for role.
- `404 Not Found`: Target entity does not exist.
- `409 Conflict`: Duplicate complaint within window or optimistic lock version clash.
- `422 Unprocessable Entity`: Illegal complaint status transition or schema validation error.
- `429 Too Many Requests`: Rate limit exceeded on endpoint.
- `500 Internal Server Error`: Server or database failure.

---

## 2. Authentication Endpoints

### 2.1 Register Student
- **Method**: `POST`
- **Path**: `/api/v1/auth/register`
- **Auth**: Public
- **Request Body**:
  ```json
  {
    "name": "Aarav Patel",
    "email": "aarav.p2024@vitstudent.ac.in",
    "password": "Password123!",
    "roomNumber": "402",
    "hostelBlock": "D"
  }
  ```
- **Response** (`201 Created`):
  ```json
  {
    "message": "Registration successful",
    "userId": 15,
    "email": "aarav.p2024@vitstudent.ac.in"
  }
  ```

### 2.2 Login
- **Method**: `POST`
- **Path**: `/api/v1/auth/login`
- **Auth**: Public (Rate-limited: 5 requests / min)
- **Request Body**:
  ```json
  {
    "email": "admin@vit.ac.in",
    "password": "adminpassword"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "message": "Login successful",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "userId": 1,
      "name": "System Administrator",
      "email": "admin@vit.ac.in",
      "role": "ADMIN",
      "dashboard": "admin-dashboard",
      "permissions": ["all"]
    }
  }
  ```
  *Also sets HttpOnly `token` cookie for browser sessions.*

### 2.3 Logout
- **Method**: `POST`
- **Path**: `/api/v1/auth/logout`
- **Auth**: Logged in user
- **Response** (`200 OK`):
  ```json
  {
    "message": "Logged out successfully"
  }
  ```

### 2.4 Get Current User Profile
- **Method**: `GET`
- **Path**: `/api/v1/auth/me`
- **Auth**: `STUDENT`, `ADMIN`, or `STAFF`
- **Response** (`200 OK`):
  ```json
  {
    "userId": 5,
    "name": "Rohan Gupta",
    "email": "rohan.g2023@vitstudent.ac.in",
    "role": "STUDENT",
    "dashboard": "STUDENT_PORTAL",
    "permissions": ["CREATE_COMPLAINT", "VIEW_OWN_COMPLAINTS", "GIVE_FEEDBACK"],
    "roomNumber": "301",
    "hostelBlock": "A"
  }
  ```

### 2.5 Change Password
- **Method**: `POST`
- **Path**: `/api/v1/auth/change-password`
- **Auth**: Logged in user
- **Request Body**:
  ```json
  {
    "currentPassword": "oldPassword123",
    "newPassword": "newSecurePassword456"
  }
  ```

---

## 3. Student Complaint Endpoints

### 3.1 Register Complaint
- **Method**: `POST`
- **Path**: `/api/v1/complaints`
- **Auth**: `STUDENT` (Rate-limited: 5 submissions / min)
- **Duplicate Rule**: Rejects identical category & description for the same student within 10 minutes (`409 Conflict`).
- **Keyword Auto-Priority**: Descriptions containing keywords like `"short circuit"`, `"sparking"`, `"flooding"` are automatically promoted to `HIGH`.
- **Request Body**:
  ```json
  {
    "category": "ELECTRICAL",
    "description": "Switchboard in room 301 is sparking and causing short circuit.",
    "suggestedPriority": "HIGH"
  }
  ```
- **Response** (`201 Created`):
  ```json
  {
    "message": "Complaint registered successfully",
    "complaintId": "HCM-2026-000041",
    "category": "Electrical",
    "priority": "High (24h SLA)",
    "status": "NEW",
    "dueBy": "2026-10-20T22:00:00"
  }
  ```

### 3.2 View My Complaints
- **Method**: `GET`
- **Path**: `/api/v1/complaints/mine?page=0&size=20`
- **Auth**: `STUDENT`
- **Response** (`200 OK`):
  ```json
  {
    "complaints": [
      {
        "complaintId": "HCM-2026-000041",
        "category": "ELECTRICAL",
        "categoryDisplay": "Electrical",
        "description": "Switchboard sparking in room 301",
        "priority": "HIGH",
        "priorityDisplay": "High (24h SLA)",
        "priorityLevel": 1,
        "status": "NEW",
        "statusDisplay": "New",
        "createdDate": "2026-10-19T22:00:00",
        "updatedDate": "2026-10-19T22:00:00",
        "resolvedDate": null,
        "dueBy": "2026-10-20T22:00:00",
        "staffId": null,
        "staffName": "Unassigned",
        "hasFeedback": false
      }
    ],
    "page": 0,
    "size": 20
  }
  ```

### 3.3 Get Single Complaint Details & Timeline
- **Method**: `GET`
- **Path**: `/api/v1/complaints/{id}`
- **Auth**: `STUDENT` (own complaint), `ADMIN`, or assigned `STAFF`
- **Response** (`200 OK`):
  Returns full metadata, audit timeline history (`complaint_history`), feedback, and comment thread.

### 3.4 Reopen Resolved Complaint
- **Method**: `POST`
- **Path**: `/api/v1/complaints/{id}/reopen`
- **Auth**: `STUDENT` (own complaint)
- **Constraint**: Allowed only if status is `RESOLVED` and within 7 days of resolution.
- **Request Body**:
  ```json
  {
    "reason": "Fan stopped spinning again after 2 hours."
  }
  ```

### 3.5 Close Resolved Complaint
- **Method**: `POST`
- **Path**: `/api/v1/complaints/{id}/close`
- **Auth**: `STUDENT` (own complaint)
- **Constraint**: Allowed when status is `RESOLVED`.

### 3.6 Submit Feedback
- **Method**: `POST`
- **Path**: `/api/v1/complaints/{id}/feedback`
- **Auth**: `STUDENT` (own complaint)
- **Constraint**: Allowed only once after resolution (1:1 relation). Rating 1 to 5.
- **Request Body**:
  ```json
  {
    "rating": 5,
    "comment": "Quick response! Electrician replaced capacitor promptly."
  }
  ```

### 3.7 Add Discussion Comment
- **Method**: `POST`
- **Path**: `/api/v1/complaints/{id}/comments`
- **Auth**: `STUDENT`, `ADMIN`, or `STAFF`
- **Request Body**:
  ```json
  {
    "message": "I will visit your room between 2 PM and 4 PM."
  }
  ```

---

## 4. Admin Management & Analytics Endpoints

### 4.1 Filter and Search Complaints
- **Method**: `GET`
- **Path**: `/api/v1/admin/complaints`
- **Query Params**:
  - `status`: `NEW`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`
  - `category`: `ELECTRICAL`, `PLUMBING`, `WIFI`, etc.
  - `priority`: `1`, `2`, `3`
  - `block`: `A`, `B`, `C`, `D`
  - `search`: free-text substring search across descriptions
  - `sortBy`: `priority`, `created_date`, `due_by`
  - `sortDir`: `ASC`, `DESC`
  - `page`: default `0`
  - `size`: default `20`
- **Auth**: `ADMIN`

### 4.2 Override Complaint Priority
- **Method**: `PUT`
- **Path**: `/api/v1/admin/complaints/{id}/priority`
- **Auth**: `ADMIN`
- **Request Body**:
  ```json
  {
    "priority": 1
  }
  ```

### 4.3 Manual Staff Assignment
- **Method**: `POST`
- **Path**: `/api/v1/admin/complaints/{id}/assign`
- **Auth**: `ADMIN`
- **Request Body**:
  ```json
  {
    "staffId": 2
  }
  ```

### 4.4 Intelligent Auto-Assign (Strategy Pattern)
- **Method**: `POST`
- **Path**: `/api/v1/admin/complaints/{id}/auto-assign`
- **Auth**: `ADMIN`
- **Logic**: Evaluates available staff matching the complaint's specialization category, then selects the staff member with the least active workload.

### 4.5 List All Maintenance Staff
- **Method**: `GET`
- **Path**: `/api/v1/admin/staff`
- **Auth**: `ADMIN`
- **Response** (`200 OK`):
  List of staff with active complaint workload count and average rating.

### 4.6 Register New Staff Member
- **Method**: `POST`
- **Path**: `/api/v1/admin/staff`
- **Auth**: `ADMIN`
- **Request Body**:
  ```json
  {
    "name": "Kavitha R",
    "email": "kavitha.staff@vit.ac.in",
    "password": "staffPassword123",
    "specialization": "WIFI"
  }
  ```

### 4.7 Admin Analytics Dashboard
- **Method**: `GET`
- **Path**: `/api/v1/admin/analytics`
- **Auth**: `ADMIN`
- **Response** (`200 OK`):
  ```json
  {
    "totalComplaints": 42,
    "openComplaints": 12,
    "resolvedComplaints": 22,
    "slaBreachRate": 4.8,
    "averageResolutionHours": 18.5,
    "statusCounts": {
      "NEW": 5,
      "ASSIGNED": 3,
      "IN_PROGRESS": 4,
      "RESOLVED": 22,
      "CLOSED": 8
    },
    "categoryCounts": {
      "ELECTRICAL": 14,
      "PLUMBING": 10,
      "WIFI": 9,
      "AC": 5,
      "CARPENTRY": 2,
      "CLEANING": 2
    },
    "blockCounts": {
      "A": 12,
      "B": 10,
      "C": 8,
      "D": 12
    },
    "dailyTrend30Days": {
      "2026-10-01": 2,
      "2026-10-02": 3
    }
  }
  ```

### 4.8 Export Complaints to CSV
- **Method**: `GET`
- **Path**: `/api/v1/admin/export.csv`
- **Auth**: `ADMIN`
- **Response Header**: `Content-Disposition: attachment; filename="complaints-export.csv"`
- **Response Body**: Comma-separated CSV data with all complaint attributes and timestamps.

---

## 5. Maintenance Staff Endpoints

### 5.1 View Assigned Tasks
- **Method**: `GET`
- **Path**: `/api/v1/staff/tasks`
- **Auth**: `STAFF`
- **Response** (`200 OK`):
  List of complaints currently assigned to the authenticated staff member.

### 5.2 Accept Task
- **Method**: `POST`
- **Path**: `/api/v1/staff/tasks/{id}/accept`
- **Auth**: `STAFF`
- **Status Change**: Transitions from `ASSIGNED` to `IN_PROGRESS`.

### 5.3 Update Task Status / Resolve
- **Method**: `PUT`
- **Path**: `/api/v1/staff/tasks/{id}/status`
- **Auth**: `STAFF`
- **Request Body**:
  ```json
  {
    "status": "RESOLVED",
    "note": "Replaced faulty switchboard breaker."
  }
  ```

### 5.4 Toggle Staff Availability
- **Method**: `PUT`
- **Path**: `/api/v1/staff/availability`
- **Auth**: `STAFF`
- **Request Body**:
  ```json
  {
    "available": false
  }
  ```

---

## 6. System & Common Endpoints

### 6.1 Notifications
- **Method**: `GET`
- **Path**: `/api/v1/notifications?limit=20`
- **Auth**: Authenticated user
- **Response**: List of user notifications with `readFlag` and timestamp.

### 6.2 Mark Notification Read
- **Method**: `POST`
- **Path**: `/api/v1/notifications/{id}/read`
- **Auth**: Authenticated user

### 6.3 Health Check
- **Method**: `GET`
- **Path**: `/health`
- **Auth**: Public
- **Response** (`200 OK`):
  ```json
  {
    "status": "UP",
    "database": "UP",
    "timestamp": 1792437600000
  }
  ```

### 6.4 Metrics-Lite
- **Method**: `GET`
- **Path**: `/metrics-lite`
- **Auth**: Public
- **Response** (`200 OK`):
  ```json
  {
    "javaVersion": "21.0.2",
    "uptimeSeconds": 3600,
    "activeThreads": 18,
    "memoryUsedMb": 74,
    "memoryMaxMb": 512,
    "dbPoolStats": "active=1, idle=3, total=4, waiting=0",
    "notificationQueueDepth": 0
  }
  ```
