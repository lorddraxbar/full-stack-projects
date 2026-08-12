# Client Portal

Welcome to the **Strategic Engineering Consultancy Client Portal** — your centralized hub for managing projects, communicating with our team, tracking updates, and accessing all project-related documents.

---

## 🏗️ Architecture

The Client Portal is built using a containerized, microservices-based architecture. Each component is independently deployable via Docker, enabling scalability, isolation, and streamlined CI/CD pipelines.

### System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Load Balancer / Reverse Proxy            │
│                      (Nginx / Traefik / Caddy)                  │
└──────┬──────────────────────────────┬───────────────────────────┘
       │                              │
       ▼                              ▼
┌──────────────────┐          ┌──────────────────┐
│   Frontend       │          │   Backend API    │
│   Vue 3          │─────────▶│   Spring Boot    │
│   TypeScript     │  HTTPS   │   Java 21        │
│   Tailwind CSS   │          │   REST API       │
└──────────────────┘          └──────┬───────────┘
                                     │
                                     ▼
                              ┌──────────────────┐
                              │   PostgreSQL     │
                              │   Database       │
                              └──────────────────┘

┌──────────────────┐
│   Landing Page   │
│   Vue 3          │─────────▶│   Backend API    │
│   TypeScript     │  HTTPS   │   (Reviews API)  │
│   Tailwind CSS   │          └──────────────────┘
└──────────────────┘
```

### Deployable Components

| Component | Technology | Docker Image | Description |
|-----------|-----------|--------------|-------------|
| **Frontend** | Vue 3, TypeScript, Tailwind CSS, Vite, shadcn-vue | `client-portal-frontend:latest` | Client-facing SPA served via Nginx |
| **Landing Page** | Vue 3, TypeScript, Tailwind CSS, Vite | `client-portal-landing:latest` | Marketing/public-facing website served via Nginx |
| **Backend** | Spring Boot 4.0.2, Java 21 | `client-portal-backend:latest` | REST API, authentication, business logic |
| **Database** | PostgreSQL 17 | `postgres:17` | Relational data storage |

### Project Structure

> **IMPORTANT:** Keep the front-end and API (backend) code in **separate folders**. The frontend code lives in a `web/` directory and the backend/API code lives in an `api/` directory (all inside the `secphils` project root). Do not mix them — each has its own independent package/dependency management and Docker build.

```
secphils/
├── web/               # Vue 3 + TypeScript + Tailwind CSS + shadcn-vue (Portal SPA)
│   ├── src/
│   ├── package.json
│   └── Dockerfile
├── landing/           # Vue 3 + TypeScript + Tailwind CSS (Marketing Landing Page)
│   ├── src/
│   ├── package.json
│   └── Dockerfile
├── api/               # Spring Boot + Java 21 (REST API)
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
└── docker-compose.yml # Orchestrates all 4 services
```

### UI/UX Requirements

All interfaces must be:
- **Fully Responsive** — Optimized for desktop, tablet, and mobile viewports using Tailwind CSS's responsive utilities
- **Theme Support** — Three theme modes with seamless switching:
  - **Light Mode** — Default light theme
  - **Dark Mode** — Dark theme for low-light environments
  - **System Mode** — Automatically follows the user's OS preference
- **Accessibility** — WCAG 2.1 AA compliant — proper contrast ratios, keyboard navigation, ARIA labels
- **Consistent Design System** — Built on shadcn-vue components with Tailwind CSS for a cohesive look across all views

### Docker Compose Setup

```yaml
version: '3.9'

services:
  # Frontend - Vue 3 Portal SPA
  frontend:
    build:
      context: ./web
      dockerfile: Dockerfile
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - backend
    networks:
      - portal-network
    restart: unless-stopped

  # Landing Page - Vue 3 Marketing Website
  landing:
    build:
      context: ./landing
      dockerfile: Dockerfile
    ports:
      - "3000:80"
    depends_on:
      - backend
    networks:
      - portal-network
    restart: unless-stopped

  # Backend - Spring Boot API
  backend:
    build:
      context: ./api
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://database:5432/clientportal
      - SPRING_DATASOURCE_USERNAME=${DB_USERNAME}
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - SPRING_JPA_HIBERNATE_DDL_AUTO=update
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}
    depends_on:
      - database
    networks:
      - portal-network
    restart: unless-stopped
    volumes:
      - uploads:/app/uploads  # Persistent storage for uploaded files

  # Database - PostgreSQL
  database:
    image: postgres:17
    environment:
      POSTGRES_DB: clientportal
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    networks:
      - portal-network
    restart: unless-stopped
    volumes:
      - postgres-data:/var/lib/postgresql/data

networks:
  portal-network:
    driver: bridge

volumes:
  postgres-data:
  uploads:
```

### Environment Variables

Create a `.env` file in the project root:

```env
# Database
DB_USERNAME=cronflow
DB_PASSWORD=<secure-password>

# Backend Profile
SPRING_PROFILES_ACTIVE=prod

# Frontend API URL
VITE_FRONTEND_API_URL=http://localhost:8080

# Landing Page API URL
VITE_LANDING_API_URL=http://localhost:8080/api/landing
```

### Dockerfile Examples

**Frontend (`web/Dockerfile`):**
```dockerfile
# Build stage
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**Landing Page (`landing/Dockerfile`):**
```dockerfile
# Build stage
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**Backend (`api/Dockerfile`):**
```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Production stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Network Architecture

```
┌─────────────┐     ┌──────────────────────┐     ┌─────────────────┐
│   Frontend   │────▶│   Internal Network   │────▶│   PostgreSQL    │
│  Vue 3 +     │     │   (Docker Compose    │     │   + Data        │
│  Tailwind    │     │    bridge)           │     │   Volumes       │
└─────────────┘     └──────────────────────┘     └─────────────────┘
                          │
                          ▼
                    ┌──────────────┐
                    │  File Uploads│
                    │  (Volume)    │
                    └──────────────┘
```

- **Frontend** communicates with **Backend** via internal Docker network (no external exposure)
- **Backend** connects to **PostgreSQL** via internal network
- **File uploads** are persisted via Docker volumes
- **External access** is routed through the load balancer/reverse proxy

### Deployment Commands

```bash
# Build and start all services
docker compose up --build -d

# View logs
docker compose logs -f frontend
docker compose logs -f backend
docker compose logs -f database

# Stop all services
docker compose down

# Stop and remove volumes (⚠️ deletes all data)
docker compose down -v

# Backup database
docker compose exec database pg_dump -U cronflow clientportal > backup.sql

# Restore database
docker compose exec -T database psql -U cronflow clientportal < backup.sql
```

---

## 📌 Get Started: Create Your Account

To access the portal, create your account using your email or sign up with one of the social SSO providers below. The person creating the account automatically serves as the company's **Authorized Representative** — the primary contact for all portal communications, project correspondence, and approvals.

### Sign Up Options

| Method | Description |
|--------|-------------|
| **Email Registration** | Create an account with your email and password |
| **Google SSO** | Sign up with your Google account |
| **Microsoft SSO** | Sign up with your Microsoft account |
| **LinkedIn SSO** | Sign up with your LinkedIn account |

### Authorized Representative Information
- **Full Name**
- **Job Title**
- **Email Address**
- **Phone Number**

### Business & Company Information
Please provide the following details to complete your company profile:

#### Company Overview
- **Company Name**
- **Location** (Headquarters / Primary Facility)
- **Company Owner**
- **Business Type / Description** — Brief description of your business operations and industry

#### Project & Production Details
- **Total Project Cost** — Estimated or actual project investment amount

#### Raw Materials
- **Raw Materials (in Tons)** — List each raw material with:
  - Material name
  - Total quantity used or purchased per month or year (in tons)

#### Production Output
- **Output in Tons per Year** — List each finished product with:
  - Product name
  - Annual production volume (in tons)

- **Amount of Product/Output per Year in Tons** — Breakdown by:
  - Total quantity per month
  - Total quantity per year

#### Waste Management
- **Waste Management Practices** — How do you manage your wastes?
  - Recyclable materials: describe processes and quantities
  - Non-recyclable materials: describe disposal methods and quantities
- **Amount of Waste Material per Month** — Total waste generated monthly (in tons), categorized by type

#### Manufacturing Process
- **Manufacturing Procedure** — Step-by-step process for how you manufacture your products/output:
  - Describe each production stage in detail
  - Include processing methods, equipment used, and quality control measures

- **Production Flowchart** — Upload or submit a visual flowchart of your production process:
  - Supported formats: PDF, PNG, JPG, SVG, or image files
  - This helps us understand your workflow and identify optimization opportunities

Once your account is created, you will receive a confirmation email with your login credentials.

---

## 🔄 New Project Creation Wizard

When a provider clicks **New Project** from the All Projects page, a wizard opens with two scenarios determined by the first screen.

### Scenario Selection (Screen 1)

The provider is presented with a dropdown of existing customer companies with **New** as the default value at the top.

| Option | Behavior |
|--------|----------|
| **New** | Triggers **Scenario A** — New Customer + Project Onboarding |
| **Existing Company** | Triggers **Scenario B** — New Project for Existing Customer |

### Scenario A: New Customer + Project Onboarding

The provider fills in both customer company information and project details.

| Step | Section | Fields |
|------|---------|--------|
| **Step 1** | **Customer Company** | Company Name, Location, Company Owner, Business Type / Description |
| **Step 2** | **Authorized Representative** | Full Name, Job Title, Email Address, Phone Number |
| **Step 3** | **Project Overview** | Project Name, Service Type, Description, Estimated Start Date, Estimated Completion Date |
| **Step 4** | **Finish** | Submit wizard |

**Post-Submission:**
- An email is sent to the authorized representative with a link to review the provided information and complete any additional details as needed. Login credentials are created if this is a new customer.

### Scenario B: New Project for Existing Customer

The provider selects an existing customer from the dropdown and fills in only the project details. Customer company information is skipped.

| Step | Section | Fields |
|------|---------|--------|
| **Step 1** | **Customer Selection** | Dropdown of existing companies |
| **Step 2** | **Authorized Representative** | Auto-filled from company profile (editable — can select different rep or invite new person) |
| **Step 3** | **Project Overview** | Project Name, Service Type, Description, Estimated Start Date, Estimated Completion Date |
| **Step 4** | **Finish** | Submit wizard |

**Post-Submission:**
- An email is sent to the authorized representative with a link to review the provided information and complete any additional details as needed.

### Notifications

Notifications are sent for all key events to keep the right people informed. Channels: **In-App** (bell icon / notification center) and **Email** (delivered to the user's registered email address). Users can control which notifications they receive via **Notification Preferences** in Settings.

| Event | Recipients | Channel |
|-------|-----------|---------|
| **Wizard submitted** (new customer) | Provider (review/complete details), Authorized Representative (review + create account) | Email |
| **Wizard submitted** (existing customer) | Provider (review/complete details), Authorized Representative (review) | Email |
| **Project created** | Assigned team members, customer company members | In-App + Email |
| **New message posted** in project conversation | All conversation participants (except sender) | In-App + Email |
| **Project update posted** (dated comment) | Customer company members | In-App + Email |
| **Document uploaded** | Provider team and customer company members (project participants) | In-App + Email |
| **Document requested** from client | Customer Authorized Representative / company members | In-App + Email |
| **Document request fulfilled** | Provider team | In-App + Email |
| **Task assigned** | Task assignee | In-App + Email |
| **Task status changed** | Project participants | In-App + Email |
| **Project status changed** | All project participants | In-App + Email |
| **Announcement published** | Target audience (project/company) | In-App + Email |
| **Team member invited** | Invited person | Email (invitation + account setup link) |

---

## 🚀 Project & Service Management

### My Projects
View and manage all active and past projects assigned to you.

- **Project Dashboard** — Overview of all projects with status indicators (Not Started, In Progress, On Hold, Completed)
- **Project Details** — Scope, objectives, deliverables, and assigned team members
- **Service Catalog** — Browse and request additional services (e.g., feasibility studies, process optimization, engineering design, compliance audits)
- **Task Tracking** — View assigned tasks and dependencies

### Project Status
Each project displays real-time status:
| Status | Description |
|---|---|
| 🟡 **Not Started** | Project is planned but has not commenced |
| 🔵 **In Progress** | Active work is underway |
| 🟠 **On Hold** | Temporarily paused — awaiting client input or external dependencies |
| 🟢 **Completed** | All deliverables have been delivered |

---

## 💬 Communication Center

Stay connected with our team through the portal's built-in communication tools.

### Project Group Conversations
Each project has its own dedicated group conversation, keeping all communication in one place.
- **One Thread per Project** — Every project has a single shared conversation
- **Project Team & Client** — Any provider team member assigned to the project and any customer company member for the project can participate
- **Shared Context** — All participants see the same messages, keeping everyone aligned
- **Reply Notifications** — All conversation participants are notified (in-app and email) when a new message is posted, so no one misses a reply
- **Read Receipts** — Know when your messages have been seen

### Announcements
- **Project Updates** — Official announcements from the consultancy team
- **Company News** — Important updates about services, policies, or events
- **Scheduled Maintenance** — Notifications about portal downtime or upgrades

### Video & Audio Calls
- **Schedule Meetings** — Book calls directly from the portal
- **Meeting Notes** — Access shared notes and action items from past meetings
- **Call Recordings** — Replay important discussions (with consent)

---

## 📤 Document Upload & Management

### Upload Documents
Clients can securely upload requested or required documents:

1. Navigate to the **Documents** section of your project
2. Click **Upload File**
3. Select the file(s) from your device
4. Add a description or tags (optional)
5. Click **Submit**

**Supported Formats:** PDF, DOCX, XLSX, PNG, JPG, DWG, STEP, IGES
**Maximum File Size:** 100 MB per file

### Document Categories
- **Client-Submitted** — Documents you have uploaded
- **Requested by Consultant** — Documents our team has requested from you

### Version Control
- Track document versions and changes over time
- Roll back to previous versions if needed
- View upload and modification history

---

## 📋 Project Updates & Activity Feed

Providers can add dated comments and updates to keep you informed. This creates a living history of your project — a clear, chronological record of what's been done, what's happening, and what's next.

### How It Works
- **Add Updates** — Providers can post dated comments with progress notes, decisions made, next steps, or any relevant information
- **View History** — See a complete chronological timeline of all project activity
- **Stay Informed** — Know exactly where things stand without needing to ask
- **Historical Record** — Access past updates to understand the full story of your project

### What Gets Logged
- Progress updates from the provider
- Key decisions
- Document submissions and reviews
- Status changes
- Important discussions and decisions

---

## 📎 Shared Documents & Resources

Access all documents that are due or relevant to you.

### Deliverables
- **Drafts** — Work-in-progress documents for your review
- **Final Versions** — Finalized deliverables
- **Templates** — Standardized forms and templates used in your project
- **Contracts & Agreements** — Signed agreements, NDAs, and SOWs

### Resource Library
- **Industry Reports** — Research and benchmarking documents
- **Best Practice Guides** — Engineering and operational best practices
- **Training Materials** — Educational content related to your project
- **Regulatory References** — Applicable standards and compliance documents

### Document Sharing
- **Share with Team** — Grant access to specific documents with individual clients or internal team members
- **Download & Export** — Download documents in multiple formats
- **Comments & Annotations** — Leave feedback directly on shared documents

---

## ⭐ Customer Reviews & Ratings

After project completion, clients can leave reviews and ratings for the consultancy. These reviews are submitted for provider approval before appearing on the public marketing landing page.

### How It Works
- **Post-Project Review** — Once a project status changes to **Completed**, the client receives an invitation to leave a review
- **Rating Scale** — 1 to 5 stars based on overall satisfaction
- **Written Feedback** — Optional detailed comments about the experience
- **Provider Approval** — All reviews are reviewed and approved/rejected by the provider before going live
- **Landing Page Display** — Approved reviews appear on the public marketing landing page to showcase client satisfaction

### Review Submission (Customer View)

| Field | Type | Description |
|-------|------|-------------|
| Project | dropdown (dynamic, projects) | Select the completed project to review |
| Star Rating | radio/rating | 1-5 star rating system |
| Review Title | text | Brief summary of experience |
| Review Body | textarea | Detailed feedback and comments |
| Would Recommend | toggle/yes-no | Yes or No recommendation |
| Submit | button | Submit for provider approval |

### Review Management (Admin View)

| Field | Type | Description |
|-------|------|-------------|
| Review List | table | All submitted reviews with status |
| Customer | text | Reviewing client name/company |
| Project | text | Associated project |
| Star Rating | stars | 1-5 rating |
| Review Title | text | Review headline |
| Review Body | textarea | Full review content |
| Status | dropdown (static, managed) | Pending / Approved / Rejected |
| Approved Date | date/time | When review was published |
| Actions | buttons | Approve / Reject / Edit |

### Landing Page Display (Public)

| Element | Description |
|---------|-------------|
| Average Rating | Aggregate star rating across all approved reviews |
| Review Count | Total number of approved reviews |
| Review Cards | Individual approved reviews with star rating, title, and excerpt |
| Full Review | Expandable/clickable to read full review content |
| Client Name | Displayed (with permission) or anonymized |
| Project Type | Category of service provided |

---

## 🔒 Security & Privacy

Your data is protected with enterprise-grade security:

- **Encrypted Transmissions** — All data is encrypted in transit (TLS 1.3)
- **Encrypted Storage** — Files and data are encrypted at rest (AES-256)
- **Role-Based Access Control** — Only authorized personnel can access specific information
- **Two-Factor Authentication (2FA)** — Optional additional layer of security
- **Audit Logs** — Full history of access and modifications
- **GDPR & Data Protection Compliance** — Your data privacy is our priority

---

## 🗺️ Sitemap

The portal is structured into **three distinct views** based on user roles. Each view provides access to specific features and pages tailored to the user's responsibilities.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CLIENT PORTAL SITEMAP                             │
├──────────────────┬──────────────────────────┬───────────────────────────────┤
│   CUSTOMER       │   SERVICE PROVIDER       │        ADMIN                  │
│   (Client View)  │   (Non-Admin View)       │     (Provider Admin View)     │
├──────────────────┼──────────────────────────┼───────────────────────────────┤
│ • Dashboard      │ • Dashboard              │ • Dashboard                   │
│ • My Projects    │ • All Projects           │ • All Projects                │
│   └─ Drill-down  │   └─ Drill-down          │   └─ Drill-down               │
│ • Documents      │ • My Tasks               │ • Documents                   │
│ • Messages       │ • Documents              │ • Communication Center        │
│ • Announcements  │ • Messages               │ • Announcements               │
│ • Settings       │ • Announcements          │ • User Management             │
│                  │ • Settings               │ • Company Settings            │
│                  │                          │   └─ Company Profile          │
│                  │                          │   └─ Team Management        │
│                  │                          │   └─ Role & Permission Management │
│                  │                          │ • Service Catalog Management        │
│                  │                          │ • Project Configuration       │
│                  │                          │   └─ Dropdown Value Management│
│                  │                          │ • System Settings             │
│                  │                          │ • Audit Logs                  │
├──────────────────┴──────────────────────────┴───────────────────────────────┤
│ Global: 🔔 Notification Center (bell icon in header, available in all views) │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 🔔 Notification Center (Global)

*Available to all users across all views via the bell icon in the header.*

| Feature | Description |
|---------|-------------|
| **Unread Badge** | Bell icon shows a count of unread notifications |
| **Notification List** | Dropdown/panel listing recent notifications (newest first) with type icon (message, document, task, project, announcement) |
| **Mark as Read** | Mark individual notifications as read, or mark all as read |
| **Click to Navigate** | Clicking a notification navigates directly to the relevant item (conversation, document, task, project) |
| **Preferences Link** | Shortcut to Notification Preferences in Settings |

### 1. Customer View (Client)

*Accessible to clients/customers who have been onboarded to the portal.*

| Page | Description |
|------|-------------|
| **Dashboard** | Overview of all assigned projects, recent activity, key metrics, and latest project updates from your consultants |
| **My Projects** | List of all projects assigned to the client with status indicators. Clicking a project drills down into the **Project Detail** page (see below) |
| **Documents** | View, upload, and manage project documents — shared files and requested documents |
| **Messages** | Participate in the project group conversation with your company's team and the consultancy team |
| **Announcements** | View official updates from the consultancy team |
| **Settings** | Account settings, personal information, password, 2FA configuration, notification preferences, team member invitations, **company profile** (company name, address, contact details, business type, client team members) |

#### Project Detail — Drill-down (from My Projects)

*Accessible by clicking on any project from the My Projects list.*

| Section | Description |
|---------|-------------|
| **Overview** | Project scope, objectives, status, service type, dated updates & activity feed (chronological record of what's been done and what's next) |
| **Documents** | Project-specific documents — shared files and requested documents |
| **Messages** | Project-specific group conversation with your company's team and the consultancy team |

### 2. Service Provider View (Non-Admin)

*Accessible to consultants, project managers, and team members who are not administrators.*

| Page | Description |
|------|-------------|
| **Dashboard** | Overview of recent tasks, active projects, pending messages, and latest project updates |
| **All Projects** | Complete list of all projects the provider has access to, with filtering and search. Clicking a project drills down into the **Project Detail** page (see below). **New Project** button to open the project creation wizard |
| **My Tasks** | View and manage individual tasks assigned to the provider across all projects — due dates, status, priority |
| **Documents** | View, upload, comment on, and manage project documents — deliverables, client submissions, version control |
| **Messages** | Participate in project group conversations with clients and team members |
| **Announcements** | Create and publish project/company announcements |
| **Settings** | Personal information, notification preferences, communication settings |

#### Project Detail — Drill-down (from All Projects)

*Accessible by clicking on any project from the All Projects list.*

| Section | Description |
|---------|-------------|
| **Overview** | Project scope, objectives, status, assigned team members, service type, dated updates & activity feed (chronological record of what's been done and what's next) |
| **Client Company & Team** | Client company information, authorized representative details, and assigned team members |
| **Documents** | Project-specific documents — deliverables, client submissions, version control |
| **Messages** | Project-specific group conversation with clients and team members |

### 3. Admin View (Provider Administrator)

*Accessible to system administrators with full access to all portal features.*

| Page | Description |
|------|-------------|
| **Dashboard** | System-wide overview — total clients, active projects, revenue metrics, system health |
| **All Projects** | Complete list of all projects across all clients with filtering and search. Clicking a project drills down into the **Project Detail** page (see below). **New Project** button to open the project creation wizard |
| **Documents** | Centralized document repository with advanced search, version control, and compliance tracking |
| **User Management** | Manage client accounts — create, edit, deactivate client users; assign to projects |
| **Company Settings** | Parent section for all company-related configuration (see sub-pages below) |
| ↳ **Company Profile** | Configure consultancy/company profile — business details, branding, contact information, operational data fields |
| ↳ **Team Management** | Manage internal provider/staff accounts — create, edit, deactivate team members; assign roles, projects, and permissions |
| ↳ **Role & Permission Management** | Configure custom roles and permission sets for all user types — clients, service providers (staff), and administrators; define granular access controls per role |
| **Service Catalog Management** | Create, update, and archive services offered to clients |
| **Project Configuration** | Set up project templates, define standard workflows, manage project statuses (add/edit/delete statuses, colors, descriptions, transition rules) |
| **Reviews & Ratings** | Manage customer reviews — approve/reject submitted reviews, manage approved reviews on landing page |
| **Communication Center** | System-wide announcements, communication logs |
| **System Settings** | General portal configuration, email templates, integrations, security policies |
| **Audit Logs** | Track all user actions, system changes, and access events |

#### Project Detail — Drill-down (from All Projects)

*Accessible by clicking on any project from the All Projects list.*

| Section | Description |
|---------|-------------|
| **Overview** | Full project view — scope, objectives, status, assigned team members, client company information, service type, dated updates & activity feed (chronological record of what's been done and what's next) |
| **Client Company & Team** | Client company information, authorized representative details, assigned consultant team |
| **Documents** | Project-specific documents — centralized repository with version control |
| **Messages** | Project-specific group conversation with clients and team members |
| **Admin Controls** | Full administrative control — reassign team, modify scope, adjust timelines, change status |

#### Company Settings — Sub-pages

##### Company Profile

*This is where the provider (consultancy) enters and manages their company profile, which is then used across the portal for client-facing information.*

| Field | Description |
|-------|-------------|
| **Company Name** | Official legal name of the consultancy |
| **Company Tagline / Slogan** | Brief tagline displayed on the portal |
| **Company Logo** | Upload logo (PNG, SVG, max 2MB) |
| **Brand Colors** | Primary and secondary color codes for portal theming |
| **Business Description** | Full description of the consultancy's services and expertise |
| **Industry Sector(s)** | Primary industries served (e.g., Manufacturing, Construction, Energy) |
| **Headquarters Address** | Full physical address |
| **Phone Number(s)** | Main office number, support line |
| **Email Addresses** | General inquiries, support, billing |
| **Website URL** | Company website |
| **Social Media Links** | LinkedIn, Twitter, etc. |
| **Tax / Registration Number** | For invoicing and compliance |
| **Banking / Payment Details** | For client invoicing (optional, encrypted) |
| **Operational Data Fields** | Customize the business data fields shown to clients during onboarding (e.g., Raw Materials, Output in Tons, Waste Management, Manufacturing Process) |

---
### Access Control Matrix

| Feature | Customer | Service Provider | Admin |
|---------|:--------:|:----------------:|:-----:|
| View own projects | ✅ | ✅ (assigned only) | ✅ (all) |
| View all projects | ❌ | ❌ | ✅ |
| Upload documents | ✅ | ✅ | ✅ |
| Request documents from clients | ❌ | ✅ | ✅ |
| Manage users | ❌ | ❌ | ✅ |
| Configure roles/permissions | ❌ | ❌ | ✅ |
| Manage service catalog | ❌ | ❌ | ✅ |
| View assigned service type | ✅ | ✅ | ✅ |
| System settings | ❌ | ❌ | ✅ |
| View analytics | Limited | Project-level | System-wide |
| Manage announcements | ❌ | ✅ | ✅ |
| Audit logs | ❌ | ❌ | ✅ |

---
## 🗂️ Field Mapping — All Pages

Detailed field-by-field mapping for every page in the portal, organized by view.

---

### 0. Account Creation / Onboarding

| Field | Section | Type | Required | Source |
|-------|---------|------|----------|--------|
| Full Name | Authorized Representative | text | ✅ | User input |
| Job Title | Authorized Representative | text | ✅ | User input |
| Email Address | Authorized Representative | email | ✅ | User input |
| Phone Number | Authorized Representative | tel | ✅ | User input |
| Company Name | Company Overview | text | ✅ | User input |
| Location | Company Overview | text | ✅ | User input |
| Company Owner | Company Overview | text | ✅ | User input |
| Business Type / Description | Company Overview | textarea | ✅ | User input |
| Total Project Cost | Project & Production Details | currency | ✅ | User input |
| Raw Materials | Raw Materials | table (name, tons) | ✅ | User input |
| Output in Tons per Year | Production Output | table (product, tons) | ✅ | User input |
| Amount of Product/Output per Year | Production Output | table (monthly, yearly) | ✅ | User input |
| Waste Management Practices | Waste Management | textarea | ✅ | User input |
| Amount of Waste Material per Month | Waste Management | table (type, tons) | ✅ | User input |
| Manufacturing Procedure | Manufacturing Process | textarea | ✅ | User input |
| Production Flowchart | Manufacturing Process | file upload | ✅ | User upload (PDF, PNG, JPG, SVG) |

---

### 1. Customer View

#### Dashboard

| Field | Type | Description |
|-------|------|-------------|
| Assigned Projects List | card list | All projects assigned to the client |
| Project Status | badge | Not Started / In Progress / On Hold / Completed |
| Latest Updates | timeline | Recent dated comments from consultants |
| Key Metrics | stat cards | High-level indicators |

#### My Projects

| Field | Type | Description |
|-------|------|-------------|
| Project Name | text | Link to Project Detail |
| Service Type | text | Assigned service category |
| Status | badge | Current project status |
| Client Company | text | Company name |
| Authorized Representative | text | Project rep |

#### Project Detail — Overview

| Field | Type | Description |
|-------|------|-------------|
| Project Name | text | Project title |
| Scope | textarea | Project scope |
| Objectives | textarea | Goals and objectives |
| Status | badge | Current status |
| Service Type | text | Assigned service |
| Recent Updates | timeline | Latest dated comments from the provider |
| Update History | timeline | Full chronological record of all updates |

#### Project Detail — Documents

| Field | Type | Description |
|-------|------|-------------|
| Document Name | text | File title |
| Category | **dropdown** (static, managed) | Client-Submitted / Requested |
| File | file | PDF, DOCX, XLSX, PNG, JPG, DWG, STEP, IGES |
| Version | text | Current version |
| Description | textarea | Optional notes |

#### Project Detail — Messages

| Field | Type | Description |
|-------|------|-------------|
| Group Conversation | list | Project-wide shared thread |
| Sender | text | Participant |
| Timestamp | date/time | Message time |
| Read Receipt | icon | Seen/unseen |
| Reply Box | textarea | Compose message |

#### Documents (top-level)

| Field | Type | Description |
|-------|------|-------------|
| Search Bar | text | Filter documents |
| Document List | table | All accessible documents |
| Category Filter | **dropdown** (static, managed) | Filter by category |
| Upload Button | button | Upload new document |
| Description / Tags | text | Optional metadata |

#### Messages (top-level)

| Field | Type | Description |
|-------|------|-------------|
| Project Conversations | list | One conversation per project |
| Message Thread | list | Selected project conversation |
| Compose Box | textarea | New message |
| Participant List | text | Project team + customer company members |

#### Announcements

| Field | Type | Description |
|-------|------|-------------|
| Announcement Title | text | Title |
| Category | **dropdown** (static, managed) | Project Update / Company News / Maintenance |
| Date | date | Publish date |
| Body | rich text | Announcement content |

#### Settings

| Field | Type | Description |
|-------|------|-------------|
| Full Name | text | Personal name |
| Email Address | email | Login email |
| Phone Number | tel | Contact number |
| Password | password | Account password |
| 2FA | toggle | Two-factor auth |
| Notification Preferences | checkboxes | Email/push preferences |
| Team Member Invitations | text/email | Invite clients |
| Company Name | text | Company profile |
| Address | text | Company address |
| Contact Details | text | Company contact |
| Business Type | text | Company business type |
| Client Team Members | table | Company team list |

#### 🔔 Notification Center (all views)

| Field | Type | Description |
|-------|------|-------------|
| Unread Badge | badge | Count of unread notifications on the bell icon |
| Notification List | dropdown/panel | Recent notifications, newest first, with type icon and timestamp |
| Notification Type | icon/badge | Message / Document / Task / Project / Announcement |
| Mark as Read | button | Mark single or all notifications as read |
| Navigate on Click | link | Opens the related conversation, document, task, or project |
| Preferences Shortcut | link | Jump to Notification Preferences in Settings |

---

### 2. Service Provider View

#### Dashboard

| Field | Type | Description |
|-------|------|-------------|
| Recent Tasks | list | Assigned tasks |
| Active Projects | card list | In-progress projects |
| Pending Messages | list | Unread messages in project conversations |
| Project Updates | timeline | Latest dated updates |

#### All Projects

| Field | Type | Description |
|-------|------|-------------|
| Project Name | text | Link to Project Detail |
| Client Company | text | Company name |
| Service Type | text | Assigned service |
| Status | badge | Current status |
| Assignee | text | Team member |
| New Project | button | Open the project creation wizard |

#### My Tasks

| Field | Type | Description |
|-------|------|-------------|
| Task Title | text | Task name |
| Project | text | Parent project |
| Due Date | date | Due date |
| Status | badge | Task status (e.g., To Do / In Progress / Done) |
| Priority | badge | Low / Medium / High |
| Assignee | text | Assigned person |

#### Project Detail — Overview

| Field | Type | Description |
|-------|------|-------------|
| Project Name | text | Project title |
| Scope | textarea | Project scope |
| Objectives | textarea | Goals |
| Status | badge | Current status |
| Service Type | text | Assigned service |
| Assigned Team | text | Team members |
| Add Update | textarea | Post a dated progress comment |
| Update History | timeline | Full chronological record of all updates |

#### Project Detail — Client Company & Team

| Field | Type | Description |
|-------|------|-------------|
| Company Name | text | Client company |
| Company Address | text | Client address |
| Business Type | text | Client industry |
| Authorized Representative | text | Project rep |
| Assigned Team | table | Provider team members |

#### Project Detail — Documents

| Field | Type | Description |
|-------|------|-------------|
| Document Name | text | File title |
| Category | **dropdown** (static, managed) | Deliverable / Client-Submitted / Requested |
| File | file | Attached file |
| Version | text | Version number |
| Comments | textarea | Document feedback |

#### Project Detail — Messages

| Field | Type | Description |
|-------|------|-------------|
| Group Conversation | list | Project-wide shared thread |
| Sender | text | Participant |
| Timestamp | date/time | Message time |
| Reply Box | textarea | Compose message |

#### Announcements

| Field | Type | Description |
|-------|------|-------------|
| Announcement Title | text | Title |
| Category | **dropdown** (static, managed) | Type |
| Audience | **dropdown** (static, managed) | Project / Company |
| Date | date | Publish date |
| Body | rich text | Content |
| Publish | button | Create/publish |

#### Settings

| Field | Type | Description |
|-------|------|-------------|
| Full Name | text | Personal name |
| Email Address | email | Contact email |
| Password | password | Account password |
| Notification Preferences | checkboxes | Preferences |
| Communication Settings | toggles | Message/call preferences |

---

### 3. Admin View

#### Dashboard

| Field | Type | Description |
|-------|------|-------------|
| Total Clients | stat | Client count |
| Active Projects | stat | Project count |
| Revenue Metrics | stat | Revenue figures |
| System Health | status | Backend/database status |
| Recent Activity | list | System events |

#### All Projects

| Field | Type | Description |
|-------|------|-------------|
| Project Name | text | Link to Project Detail |
| Client Company | text | Company name |
| Service Type | text | Assigned service |
| Status | badge | Current status |
| Assigned Team | text | Team members |
| New Project | button | Open the project creation wizard |

#### Project Detail — Overview

| Field | Type | Description |
|-------|------|-------------|
| Project Name | text | Project title |
| Scope | textarea | Project scope |
| Objectives | textarea | Goals |
| Status | badge | Current status |
| Assigned Team | text | Team members |
| Client Company | text | Company info |
| Service Type | text | Assigned service |
| Add Update | textarea | Post a dated progress comment |
| Update History | timeline | Full chronological record of all updates |

#### Project Detail — Client Company & Team

| Field | Type | Description |
|-------|------|-------------|
| Company Name | text | Client company |
| Company Address | text | Client address |
| Business Type | text | Client industry |
| Authorized Representative | text | Project rep |
| Assigned Consultant Team | table | Provider team |

#### Project Detail — Documents

| Field | Type | Description |
|-------|------|-------------|
| Document Name | text | File title |
| Category | **dropdown** (static, managed) | Document type |
| File | file | Attached file |
| Version | text | Version number |

#### Project Detail — Messages

| Field | Type | Description |
|-------|------|-------------|
| Group Conversation | list | Project-wide shared thread |
| Sender | text | Participant |
| Timestamp | date/time | Message time |
| Reply Box | textarea | Compose message |

#### Project Detail — Admin Controls

| Field | Type | Description |
|-------|------|-------------|
| Assign Team | **dropdown** (dynamic, team members) | Reassign team members |
| Scope | textarea | Modify project scope |
| Timeline | date | Adjust project schedule |
| Status | **dropdown** (static, managed) | Change project status |

#### Documents (central repository)

| Field | Type | Description |
|-------|------|-------------|
| Search Bar | text | Advanced search |
| Document List | table | All documents |
| Filters | **dropdown**/multi (static, managed) | Category, project, version |
| Compliance Tracking | badge | Compliance status |

#### User Management

| Field | Type | Description |
|-------|------|-------------|
| User Name | text | Client user name |
| Email | email | Login email |
| Company | text | Associated company |
| Role | **dropdown** (static, managed) | User role |
| Status | **dropdown** (static, managed) | Active / Deactivated |
| Assigned Projects | multi-select (dynamic, projects) | Project assignments |
| Actions | buttons | Edit / Deactivate |

#### Company Settings — Company Profile

| Field | Type | Description |
|-------|------|-------------|
| Company Name | text | Consultancy name |
| Company Tagline / Slogan | text | Display tagline |
| Company Logo | file | PNG/SVG logo |
| Brand Colors | color picker | Primary/secondary |
| Business Description | textarea | Services/expertise |
| Industry Sector(s) | multi-select (static, managed) | Served industries |
| Headquarters Address | text | Physical address |
| Phone Number(s) | tel | Contact numbers |
| Email Addresses | email | Contact emails |
| Website URL | url | Company website |
| Social Media Links | url | LinkedIn, Twitter |
| Tax / Registration Number | text | Compliance |
| Banking / Payment Details | text | Invoicing (encrypted) |
| Operational Data Fields | config | Custom onboarding fields |

#### Company Settings — Team Management

| Field | Type | Description |
|-------|------|-------------|
| Staff Name | text | Team member name |
| Email | email | Login email |
| Role | **dropdown** (static, managed) | Team role |
| Assigned Projects | multi-select (dynamic, projects) | Project access |
| Permissions | checkboxes | Granular perms |
| Status | **dropdown** (static, managed) | Active / Deactivated |

#### Company Settings — Role & Permission Management

| Field | Type | Description |
|-------|------|-------------|
| Role Name | text | Role label |
| User Type | **dropdown** (static, managed) | Client / Provider / Admin |
| Permissions | checkbox matrix | Per-feature access |
| Description | text | Role purpose |

#### Service Catalog Management

| Field | Type | Description |
|-------|------|-------------|
| Service Name | text | Service title |
| Description | textarea | Service details |
| Category | **dropdown** (static, managed) | Service category |
| Status | **dropdown** (static, managed) | Active / Archived |
| Price / Rate | currency | Pricing (optional) |

#### Project Configuration

| Field | Type | Description |
|-------|------|-------------|
| Template Name | text | Project template |
| Template Fields | config | Default fields |
| Workflow Steps | list | Standard workflow |
| Status Name | text | Status label |
| Status Color | color picker | Status color |
| Status Description | text | Status meaning |
| Transition Rules | matrix | Status-to-status transitions |

##### Dropdown Value Management

*Manage all static dropdown values used throughout the portal. Each dropdown category can be added, edited, reordered, and deleted.*

| Field | Type | Description |
|-------|------|-------------|
| Dropdown Category | select | Project Status / Document Category / Announcement Category / Task Status / Priority / User Role / Service Category / Audience / Industry Sector / Report Type / Other |
| Value | text | Dropdown option label |
| Color | color picker | Optional color tag for visual distinction |
| Description | textarea | Optional description of the value |
| Sort Order | number | Display order in dropdown |
| Active | toggle | Enable/disable without deleting |
| Actions | buttons | Edit / Delete / Move Up / Move Down |

**Managed Dropdown Categories:**

| Category | Values (default) | Editable |
|----------|------------------|----------|
| Project Status | Not Started / In Progress / On Hold / Completed | ✅ |
| Document Category | Client-Submitted / Requested | ✅ |
| Announcement Category | Project Update / Company News / Maintenance | ✅ |
| Task Status | To Do / In Progress / Done | ✅ |
| Priority | Low / Medium / High | ✅ |
| User Role | Client / Provider / Admin | ✅ |
| Service Category | (from Service Catalog) | ✅ |
| Audience | Project / Company | ✅ |
| Industry Sector | (custom, populated by admin) | ✅ |
| Report Type | Performance / Satisfaction / Resources / Revenue | ✅ |
| Status | Active / Deactivated / Archived | ✅ |

#### Reviews & Ratings

| Field | Type | Description |
|-------|------|-------------|
| Review List | table | All submitted reviews with status |
| Customer | text | Reviewing client name/company |
| Project | text | Associated project |
| Star Rating | stars | 1-5 rating |
| Review Title | text | Review headline |
| Review Body | textarea | Full review content |
| Status | **dropdown** (static, managed) | Pending / Approved / Rejected |
| Approved Date | date/time | When review was published |
| Actions | buttons | Approve / Reject / Edit |

#### Communication Center

| Field | Type | Description |
|-------|------|-------------|
| Announcement Title | text | Title |
| Announcement Body | rich text | Content |
| Audience | **dropdown** (static, managed) | Target recipients |
| Communication Logs | table | System comms |

#### System Settings

| Field | Type | Description |
|-------|------|-------------|
| Portal Name | text | Portal branding |
| Email Templates | rich text | Template editor |
| Integrations | config | External integrations |
| Security Policies | toggles | Password, 2FA, sessions |
| Maintenance Mode | toggle | Portal downtime |

#### Audit Logs

| Field | Type | Description |
|-------|------|-------------|
| Timestamp | date/time | Event time |
| User | text | Actor |
| Action | text | Performed action |
| Entity | text | Affected resource |
| IP Address | text | Source |
| Details | text | Change details |
