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
    "email": "aarav.patel@vitstudent.ac.in",
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
    "email": "aarav.patel@vitstudent.ac.in"
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
    "email": "rohan.gupta@vitstudent.ac.in",
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

### 2.6 Get User Profile
- **Method**: `GET`
- **Path**: `/api/v1/users/profile`
- **Auth**: `STUDENT`, `STAFF`, or `ADMIN`
- **Response** (`200 OK`):
  ```json
  {
    "userId": 5,
    "email": "rohan.gupta@vitstudent.ac.in",
    "name": "Rohan Gupta",
    "role": "STUDENT",
    "active": true,
    "createdAt": "2026-10-15T09:00:00Z",
    "dashboard": "STUDENT_PORTAL",
    "permissions": ["CREATE_COMPLAINT", "VIEW_OWN_COMPLAINTS", "GIVE_FEEDBACK"],
    "roomNumber": "301",
    "hostelBlock": "A"
  }
  ```

### 2.7 Update User Profile
- **Method**: `PUT`
- **Path**: `/api/v1/users/profile`
- **Auth**: `STUDENT`, `STAFF`, or `ADMIN`
- **Request Body**:
  ```json
  {
    "name": "Rohan Gupta",
    "roomNumber": "305",
    "hostelBlock": "B"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "message": "Profile updated successfully",
    "user": {
      "userId": 5,
      "email": "rohan.gupta@vitstudent.ac.in",
      "name": "Rohan Gupta",
      "role": "STUDENT",
      "active": true,
      "createdAt": "2026-10-15T09:00:00Z",
      "dashboard": "STUDENT_PORTAL",
      "permissions": ["CREATE_COMPLAINT", "VIEW_OWN_COMPLAINTS", "GIVE_FEEDBACK"],
      "roomNumber": "305",
      "hostelBlock": "B"
    }
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
        "studentName": "Rohan Gupta",
        "studentEmail": "rohan.gupta@vitstudent.ac.in",
        "studentRoomNumber": "301",
        "studentHostelBlock": "A",
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
  ```json
  {
    "complaint": {
      "complaintId": "HCM-2026-000041",
      "studentId": 5,
      "studentName": "Rohan Gupta",
      "studentEmail": "rohan.gupta@vitstudent.ac.in",
      "studentRoomNumber": "301",
      "studentHostelBlock": "A",
      "staffId": 2,
      "staffName": "Arun Kumar",
      "category": "ELECTRICAL",
      "categoryDisplay": "Electrical",
      "description": "Switchboard sparking in room 301",
      "priority": "HIGH",
      "priorityDisplay": "High (24h SLA)",
      "priorityLevel": 1,
      "status": "ASSIGNED",
      "statusDisplay": "Assigned",
      "createdDate": "2026-10-19T22:00:00",
      "updatedDate": "2026-10-19T22:30:00",
      "dueBy": "2026-10-20T22:00:00",
      "resolvedDate": null,
      "allowedActions": ["VIEW"]
    },
    "history": [
      {
        "id": 101,
        "complaintId": "HCM-2026-000041",
        "oldStatus": "NEW",
        "newStatus": "ASSIGNED",
        "changedBy": 1,
        "changedByName": "Dr. Ramesh Kumar (Admin)",
        "note": "Assigned to Arun Kumar (Electrical Maintenance)",
        "changedAt": "2026-10-19T22:30:00"
      }
    ],
    "comments": [],
    "feedback": null,
    "attachments": []
  }
  ```

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

### 4.4.1 Auto-Assign Batch Preview (Read-Only)
- **Method**: `POST`
- **Path**: `/api/assignments/auto/preview` (also `/api/v1/assignments/auto/preview`, `/api/v1/admin/assignments/auto/preview`)
- **Auth**: `ADMIN`
- **Request Body**: `{ "ticketIds": ["HCM-2026-000001", ...] }`
- **Response** (`200 OK`): Proposed staff, workload delta, reason, eligible staff, warnings, summary strip counts. No writes.

### 4.4.2 Auto-Assign Batch Confirmation (Atomic Transaction)
- **Method**: `POST`
- **Path**: `/api/assignments/auto/confirm` (also `/api/v1/assignments/auto/confirm`, `/api/v1/admin/assignments/auto/confirm`)
- **Auth**: `ADMIN`
- **Request Body**: `{ "assignments": [{ "ticketId": "HCM-2026-000001", "staffId": 201 }, ...] }`
- **Response** (`200 OK` or `409 Conflict`): Saves all assignments, audit history, and notifications in one atomic transaction; returns conflicts if data went stale.

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

### 4.9 List and Search Users
- **Method**: `GET`
- **Path**: `/api/v1/admin/users`
- **Auth**: `ADMIN`
- **Query Params**:
  - `role`: Optional filter (`STUDENT`, `STAFF`, `ADMIN`)
  - `q`: Optional search substring matching name, email, hostel block, or room number
  - `page`: Page index (default `0`)
  - `size`: Page size (default `25`, max `100`)
- **Response** (`200 OK`):
  ```json
  {
    "users": [
      {
        "userId": 10,
        "email": "priya.sharma@vitstudent.ac.in",
        "name": "Priya Sharma",
        "role": "STUDENT",
        "active": true,
        "createdAt": "2026-10-15T09:00:00Z",
        "roomNumber": "402",
        "hostelBlock": "D"
      }
    ],
    "total": 54,
    "page": 0,
    "size": 25
  }
  ```

### 4.10 Update User Account (Admin Override)
- **Method**: `PUT`
- **Path**: `/api/v1/admin/users/{id}`
- **Auth**: `ADMIN`
- **Request Body**:
  ```json
  {
    "name": "Priya Sharma",
    "active": true,
    "roomNumber": "505",
    "hostelBlock": "D"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "message": "User updated successfully",
    "user": {
      "userId": 10,
      "email": "priya.sharma@vitstudent.ac.in",
      "name": "Priya Sharma",
      "role": "STUDENT",
      "active": true,
      "roomNumber": "505",
      "hostelBlock": "D"
    }
  }
  ```

### 4.11 Deactivate User Account (Soft Delete)
- **Method**: `DELETE`
- **Path**: `/api/v1/admin/users/{id}`
- **Auth**: `ADMIN`
- **Response** (`200 OK`):
  ```json
  {
    "message": "User deactivated successfully"
  }
  ```

### 4.12 Update Staff Profile (Admin)
- **Method**: `PUT`
- **Path**: `/api/v1/admin/staff/{id}`
- **Auth**: `ADMIN`
- **Request Body**:
  ```json
  {
    "specialization": "ELECTRICAL",
    "available": true
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "message": "Staff updated successfully",
    "staff": {
      "userId": 2,
      "email": "arun.kumar@vit.ac.in",
      "name": "Arun Kumar",
      "role": "STAFF",
      "active": true,
      "specialization": "ELECTRICAL",
      "specializationDisplay": "Electrical Maintenance",
      "available": true
    }
  }
  ```

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
