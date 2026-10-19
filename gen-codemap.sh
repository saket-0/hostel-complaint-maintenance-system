#!/usr/bin/env bash
set -e

# ==============================================================================
# HCMS CodeMap Generator
# Scans src/main Java files, extracts key public signatures, and generates docs/CODEMAP.md
# Keeps the total document compact and under 250 lines.
# ==============================================================================

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_FILE="$ROOT_DIR/docs/CODEMAP.md"
mkdir -p "$ROOT_DIR/docs"

python3 - << 'EOF'
import os
import re
import subprocess

root_dir = os.path.abspath(".")
output_path = os.path.join(root_dir, "docs", "CODEMAP.md")

# Known concise purposes for classes
PURPOSES = {
    # Core
    "AppConfig": "Central configuration manager reading application properties",
    "Category": "Enum of complaint categories with SLA and auto-specialization mappings",
    "ComplaintStatus": "State machine enum governing complaint lifecycle and valid transitions",
    "Priority": "Enum of severity levels (LOW, MEDIUM, HIGH, URGENT) with default SLA hours",
    "Role": "User roles (STUDENT, MAINTENANCE_STAFF, ADMIN) and authority checks",
    "Specialization": "Staff maintenance trade domains matching complaint categories",
    "AuthenticationException": "Exception thrown on bad credentials or expired tokens",
    "AuthorizationException": "Exception thrown on role permission violations",
    "ConcurrentModificationException": "Exception for optimistic locking / state conflicts",
    "DataAccessException": "Wrapper exception for underlying JDBC/SQL errors",
    "DuplicateEntryException": "Exception when unique constraints or duplicate checks fail",
    "HcmsException": "Base unchecked runtime exception for HCMS domain errors",
    "InvalidStatusTransitionException": "Exception when attempting an illegal complaint state transition",
    "ResourceNotFoundException": "Exception when requested entity ID is not found",
    "ValidationException": "Exception when input DTO or payload fails validation",
    "UserFactory": "Factory pattern utility for creating Student, Staff, and Admin users",
    "AssignmentStrategy": "Strategy interface for assigning complaints to maintenance staff",
    "Auditable": "Interface for entities tracking creation and update timestamps",
    "ComplaintStatusListener": "Observer interface notified upon complaint status transitions",
    "Notifiable": "Interface for entities capable of receiving notifications",
    "Repository": "Generic repository interface defining CRUD operations",
    "Searchable": "Interface for repository criteria searches and pagination",
    "Admin": "Administrator user entity with elevated privileges",
    "Attachment": "File attachment entity metadata (mime, path, size)",
    "Complaint": "Primary domain entity tracking complaint lifecycle, student, staff, SLA",
    "ComplaintComment": "Threaded comment entity on a complaint",
    "ComplaintHistory": "Immutable audit record of complaint state/field changes",
    "Feedback": "Student rating and review entity submitted upon resolution",
    "MaintenanceStaff": "Staff user entity with specialization, availability, and active tasks",
    "Notification": "User alert entity with read/unread status",
    "Student": "Student user entity with hostel block and room number",
    "User": "Abstract base class for all system users",
    "ComplaintActionPolicy": "Policy evaluator for role-based contextual UI actions",
    "ComplaintIdGenerator": "Thread-safe sequential complaint identifier generator (HCM-YYYY-NNNNNN)",

    # Persistence
    "ComplaintDao": "JDBC DAO for complaint persistence, searches, duplicate checks, and metrics",
    "SupportDao": "JDBC DAO for complaint history, comments, attachments, feedback, notifications",
    "UserDao": "JDBC DAO for students, staff, admins, credentials, and availability",
    "DatabaseManager": "HikariCP connection pool provider and lifecycle manager",
    "RowMapper": "Functional interface for mapping JDBC ResultSet rows to domain models",
    "TransactionManager": "Executor for atomic JDBC transactions and read-only queries",
    "SeedDataValidator": "Validates database schema and seed integrity at startup",

    # Service
    "AnalyticsService": "Aggregates complaint metrics, SLA adherence, and staff efficiency",
    "AuthService": "BCrypt password hashing and JWT token issuance and validation",
    "ComplaintManager": "Core business logic coordinator: registration, triage, auto-assign, transitions",
    "SlaEscalationEngine": "Scheduled background service checking SLA breaches and escalating",
    "AuditListener": "Status listener recording state changes to ComplaintHistory",
    "NotificationService": "Dispatches in-app notifications and admin broadcasts",
    "LeastWorkloadStrategy": "Assignment strategy picking available staff with fewest open complaints",
    "SpecializationMatchStrategy": "Assignment strategy matching category to staff specialization",

    # Web
    "HcmsApplication": "Javalin web server bootstrapper, routing table, and filter registrations",
    "AdminController": "Admin dashboard, triage queue, staff provisioning, and analytics endpoints",
    "AuthController": "User registration, authentication, token refresh, and profile endpoints",
    "ComplaintController": "Complaint creation, details, comments, attachments, close/reopen, ratings",
    "HealthController": "System healthcheck, database ping, and uptime endpoints",
    "NotificationController": "User notification queries and mark-as-read endpoints",
    "StaffController": "Staff task list, availability toggling, and task status updates",
    "AuthFilter": "Javalin before-filter enforcing JWT authentication and role authorization",
    "CorsFilter": "Javalin filter adding CORS headers for local/cross-origin frontend requests",
    "RateLimitFilter": "Sliding token bucket IP rate limiter protecting against abuse",
    "GlobalExceptionHandler": "Maps domain exceptions to HTTP status codes and JSON envelopes",
}

modules = ["hcms-core", "hcms-persistence", "hcms-service", "hcms-web"]

def extract_key_methods(filepath):
    methods = []
    method_sig = re.compile(r'public\s+(?:static\s+)?(?:synchronized\s+)?[\w\<\>\[\],\s]+\s+([a-zA-Z0-9_]+)\s*\(([^)]*)\)')
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            line_str = line.strip()
            if line_str.startswith("//") or line_str.startswith("*"):
                continue
            m = method_sig.search(line_str)
            if m:
                name = m.group(1)
                # Skip constructors and trivial getters/setters unless key
                if name[0].isupper() or name in ("equals", "hashCode", "toString"):
                    continue
                if name not in methods:
                    methods.append(name + "()")
            if len(methods) >= 4:
                break
    return methods

codemap_lines = [
    "# HCMS CodeMap: Architecture & Class Directory",
    "",
    "> Auto-generated reference map. Consult this file before reading source files.",
    "> Generated with `./gen-codemap.sh` — strictly kept under 250 lines.",
    "",
    "## 1. Top 5 Largest Files (Line Counts & Major Methods)",
    ""
]

# Find top 5 largest Java files in src/main
java_files_with_counts = []
for mod in modules:
    mod_dir = os.path.join(root_dir, mod, "src", "main", "java")
    if not os.path.exists(mod_dir):
        continue
    for root, dirs, files in os.walk(mod_dir):
        for f in files:
            if f.endswith(".java"):
                full_p = os.path.join(root, f)
                with open(full_p, "r", encoding="utf-8", errors="ignore") as jf:
                    cnt = sum(1 for _ in jf)
                rel_p = os.path.relpath(full_p, root_dir)
                java_files_with_counts.append((cnt, rel_p, f[:-5]))

java_files_with_counts.sort(key=lambda x: x[0], reverse=True)

top5_detail = {
    "ComplaintDao": "save() ~L151, findById() ~L196, update() ~L222, search() ~L299, isDuplicate() ~L447, staffWorkload() ~L628",
    "ComplaintManager": "registerComplaint() ~L95, assignComplaint() ~L152, autoAssignComplaint() ~L195, updateComplaintStatus() ~L227, reopenComplaint() ~L272",
    "SupportDao": "saveHistory() ~L34, findHistoryByComplaintId() ~L58, saveFeedback() ~L124, saveComment() ~L182, saveNotification() ~L237",
    "AdminController": "triage() ~L49, listComplaints() ~L188, assign() ~L239, autoAssign() ~L250, listStaff() ~L260, analytics() ~L298",
    "ComplaintController": "create() ~L40, getMyComplaints() ~L61, getById() ~L77, uploadAttachment() ~L156, reopen() ~L196, close() ~L207"
}

for cnt, rel_p, cls_name in java_files_with_counts[:5]:
    detail = top5_detail.get(cls_name, "")
    codemap_lines.append(f"- **`{rel_p}`** ({cnt} lines)")
    if detail:
        codemap_lines.append(f"  - Key methods: {detail}")

codemap_lines.append("")
codemap_lines.append("---")
codemap_lines.append("")
codemap_lines.append("## 2. Module Class Directory (One Line Per Class)")

for mod in modules:
    mod_dir = os.path.join(root_dir, mod, "src", "main", "java")
    if not os.path.exists(mod_dir):
        continue
    codemap_lines.append(f"\n### {mod}")
    
    mod_classes = []
    for root, dirs, files in os.walk(mod_dir):
        for f in sorted(files):
            if f.endswith(".java"):
                full_p = os.path.join(root, f)
                rel_p = os.path.relpath(full_p, root_dir)
                cls_name = f[:-5]
                methods = extract_key_methods(full_p)
                purpose = PURPOSES.get(cls_name, f"Handles {cls_name} operations")
                m_str = f"; key methods: {', '.join(methods)}" if methods else ""
                mod_classes.append(f"- `{rel_p}`: {purpose}{m_str}")
    
    # Sort for deterministic output
    mod_classes.sort()
    codemap_lines.extend(mod_classes)

codemap_lines.append("")
codemap_lines.append("---")
codemap_lines.append("")
codemap_lines.append("## 3. Test Suites & Target Classes")
codemap_lines.append("")

test_mappings = [
    ("hcms-core/.../ComplaintStatusTest.java", "ComplaintStatus (valid & invalid state machine transitions)"),
    ("hcms-core/.../PriorityCategoryConfigTest.java", "Priority, Category, AppConfig (SLA hours & properties)"),
    ("hcms-core/.../UserFactoryTest.java", "UserFactory (Student, MaintenanceStaff, Admin instantiation)"),
    ("hcms-core/.../ComplaintModelTest.java", "Complaint model (invariants, timestamps, audit fields)"),
    ("hcms-core/.../ComplaintActionPolicyTest.java", "ComplaintActionPolicy (role-based permitted actions)"),
    ("hcms-core/.../ComplaintIdGeneratorTest.java", "ComplaintIdGenerator (sequential format & concurrency)"),
    ("hcms-persistence/.../DaoIntegrationIT.java", "ComplaintDao, UserDao, SupportDao (PostgreSQL integration)"),
    ("hcms-persistence/.../SeedDataValidatorTest.java", "SeedDataValidator (schema & baseline rows verification)"),
    ("hcms-service/.../AuthServiceTest.java", "AuthService (password hashing, JWT token lifecycle)"),
    ("hcms-service/.../ComplaintManagerTest.java", "ComplaintManager (registration, auto-assignment, lifecycle)"),
    ("hcms-service/.../SlaEscalationEngineTest.java", "SlaEscalationEngine (SLA calculation, periodic checks)"),
    ("hcms-service/.../StrategyTest.java", "SpecializationMatchStrategy, LeastWorkloadStrategy"),
    ("hcms-web/.../AdminTriageIT.java", "AdminController (/api/admin/triage endpoints)"),
    ("hcms-web/.../ComplaintStatusConsistencyIT.java", "ComplaintController & ComplaintStatus transitions"),
    ("hcms-web/.../ContextualActionsIT.java", "ComplaintController action authorization"),
    ("hcms-web/.../AuthFilterTest.java", "AuthFilter (Bearer token validation & 401 handling)"),
    ("hcms-web/.../RateLimitFilterTest.java", "RateLimitFilter (token bucket request throttling)"),
    ("hcms-web/.../GlobalExceptionHandlerTest.java", "GlobalExceptionHandler (exception-to-HTTP mapping)")
]

for test_path, target in test_mappings:
    codemap_lines.append(f"- `{test_path}` $\\rightarrow$ Tests **{target}**")

content = "\n".join(codemap_lines) + "\n"
total_lines = len(codemap_lines)

with open(output_path, "w", encoding="utf-8") as out:
    out.write(content)

print(f"[✓] Generated {output_path} ({total_lines} lines).")
if total_lines > 250:
    print(f"[!] Warning: total lines ({total_lines}) exceeds 250 limit!")
EOF
