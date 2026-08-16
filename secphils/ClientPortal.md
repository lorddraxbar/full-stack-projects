# Client Portal

Welcome to the **Strategic Engineering Consultancy Client Portal** -- your centralized hub for managing projects, communicating with our team, tracking updates, and accessing all project-related documents.

---

##  Architecture

The Client Portal is built using a containerized, microservices-based architecture. Each component is independently deployable via Docker, enabling scalability, isolation, and streamlined CI/CD pipelines.

### System Overview

```
+-----------------------------------------------------------------+
|                        Load Balancer / Reverse Proxy            |
|                      (Nginx / Traefik / Caddy)                  |
+------+------------------------------+---------------------------+
       |                              |
       v                              v
+------------------+          +------------------+
|   Frontend       |          |   Backend API    |
|   Vue 3          |--------->|   Spring Boot    |
|   TypeScript     |  HTTPS   |   Java 21        |
|   Tailwind CSS   |          |   REST API       |
+------------------+          +------+-----------+
                                     |
                                     v
                              +------------------+
                              |   PostgreSQL     |
                              |   Database       |
                              +------------------+

+------------------+
|   Landing Page   |
|   Vue 3          |--------->|   Backend API    |
|   TypeScript     |  HTTPS   |   (Reviews API)  |
|   Tailwind CSS   |          +------------------+
+------------------+
```

### Deployable Components

| Component | Technology | Docker Image | Description |
|-----------|-----------|--------------|-------------|
| **Frontend** | Vue 3, TypeScript, Tailwind CSS, Vite, shadcn-vue | `client-portal-frontend:latest` | Client-facing SPA served via Nginx |
| **Landing Page** | Vue 3, TypeScript, Tailwind CSS, Vite | `client-portal-landing:latest` | Marketing/public-facing website served via Nginx |
| **Backend** | Spring Boot 4.0.2, Java 21 | `client-portal-backend:latest` | REST API, authentication, business logic |
| **Database** | PostgreSQL 17 | `postgres:17` | Relational data storage |

### Project Structure

> **IMPORTANT:** Keep the front-end and API (backend) code in **separate folders**. The frontend code lives in a `web/` directory and the backend/API code lives in an `api/` directory (all inside the `secphils` project root). Do not mix them -- each has its own independent package/dependency management and Docker build.

```
secphils/
+-- web/               # Vue 3 + TypeScript + Tailwind CSS + shadcn-vue (Portal SPA)
|   +-- src/
|   +-- package.json
|   +-- Dockerfile
+-- landing/           # Vue 3 + TypeScript + Tailwind CSS (Marketing Landing Page)
|   +-- src/
|   +-- package.json
|   +-- Dockerfile
+-- api/               # Spring Boot + Java 21 (REST API)
|   +-- src/
|   +-- pom.xml
|   +-- Dockerfile
+-- docker-compose.yml # Orchestrates all 4 services
```

### UI/UX Requirements

All interfaces must be:
- **Fully Responsive** -- Optimized for desktop, tablet, and mobile viewports using Tailwind CSS's responsive utilities
- **Theme Support** -- Three theme modes with seamless switching:
  - **Light Mode** -- Default light theme
  - **Dark Mode** -- Dark theme for low-light environments
  - **System Mode** -- Automatically follows the user's OS preference
- **Accessibility** -- WCAG 2.1 AA compliant -- proper contrast ratios, keyboard navigation, ARIA labels
- **Consistent Design System** -- Built on shadcn-vue components with Tailwind CSS for a cohesive look across all views

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
+-------------+     +----------------------+     +-----------------+
|   Frontend   |---->|   Internal Network   |---->|   PostgreSQL    |
|  Vue 3 +     |     |   (Docker Compose    |     |   + Data        |
|  Tailwind    |     |    bridge)           |     |   Volumes       |
+-------------+     +----------------------+     +-----------------+
                          |
                          v
                    +--------------+
                    |  File Uploads|
                    |  (Volume)    |
                    +--------------+
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

# Stop and remove volumes (-- deletes all data)
docker compose down -v

# Backup database
docker compose exec database pg_dump -U cronflow clientportal > backup.sql

# Restore database
docker compose exec -T database psql -U cronflow clientportal < backup.sql
```

---

##  Get Started: Create Your Account

To access the portal, create your account using your email or sign up with one of the social SSO providers below. The person creating the account automatically serves as the company's **Authorized Representative** -- the primary contact for all portal communications, project correspondence, and approvals.

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
- **Business Type / Description** -- Brief description of your business operations and industry

#### Project & Production Details
- **Total Project Cost** -- Estimated or actual project investment amount

#### Raw Materials
- **Raw Materials (in Tons)** -- List each raw material with:
  - Material name
  - Total quantity used or purchased per month or year (in tons)

#### Production Output
- **Output in Tons per Year** -- List each finished product with:
  - Product name
  - Annual production volume (in tons)

- **Amount of Product/Output per Year in Tons** -- Breakdown by:
  - Total quantity per month
  - Total quantity per year

#### Waste Management
- **Waste Management Practices** -- How do you manage your wastes?
  - Recyclable materials: describe processes and quantities
  - Non-recyclable materials: describe disposal methods and quantities
- **Amount of Waste Material per Month** -- Total waste generated monthly (in tons), categorized by type

#### Manufacturing Process
- **Manufacturing Procedure** -- Step-by-step process for how you manufacture your products/output:
  - Describe each production stage in detail
  - Include processing methods, equipment used, and quality control measures

- **Production Flowchart** -- Upload or submit a visual flowchart of your production process:
  - Supported formats: PDF, PNG, JPG, SVG, or image files
  - This helps us understand your workflow and identify optimization opportunities

Once your account is created, you will receive a confirmation email with your login credentials.

---

##  New Project Creation Wizard

When a provider clicks **New Project** from the All Projects page, a wizard opens with two scenarios determined by the first screen.

### Scenario Selection (Screen 1)

The provider is presented with a dropdown of existing customer companies with **New** as the default value at the top.

| Option | Behavior |
|--------|----------|
| **New** | Triggers **Scenario A** -- New Customer + Project Onboarding |
| **Existing Company** | Triggers **Scenario B** -- New Project for Existing Customer |

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
| **Step 2** | **Authorized Representative** | Auto-filled from company profile (editable -- can select different rep or invite new person) |
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

##  Project & Service Management

### My Projects
View and manage all active and past projects assigned to you.

- **Project Dashboard** -- Overview of all projects with status indicators (Not Started, In Progress, On Hold, Completed)
- **Project Details** -- Scope, objectives, deliverables, and assigned team members
- **Service Catalog** -- Browse and request additional services (e.g., feasibility studies, process optimization, engineering design, compliance audits)
- **Task Tracking** -- View assigned tasks and dependencies

### Project Status
Each project displays real-time status:
| Status | Description |
|---|---|
|  **Not Started** | Project is planned but has not commenced |
|  **In Progress** | Active work is underway |
|  **On Hold** | Temporarily paused -- awaiting client input or external dependencies |
|  **Completed** | All deliverables have been delivered |

---

##  Communication Center

Stay connected with our team through the portal's built-in communication tools.

### Project Group Conversations
Each project has its own dedicated group conversation, keeping all communication in one place.
- **One Thread per Project** -- Every project has a single shared conversation
- **Project Team & Client** -- Any provider team member assigned to the project and any customer company member for the project can participate
- **Shared Context** -- All participants see the same messages, keeping everyone aligned
- **Reply Notifications** -- All conversation participants are notified (in-app and email) when a new message is posted, so no one misses a reply
- **Read Receipts** -- Know when your messages have been seen

### Announcements
- **Project Updates** -- Official announcements from the consultancy team
- **Company News** -- Important updates about services, policies, or events
- **Scheduled Maintenance** -- Notifications about portal downtime or upgrades

### Video & Audio Calls
- **Schedule Meetings** -- Book calls directly from the portal
- **Meeting Notes** -- Access shared notes and action items from past meetings
- **Call Recordings** -- Replay important discussions (with consent)

---

##  Document Upload & Management

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
- **Client-Submitted** -- Documents you have uploaded
- **Requested by Consultant** -- Documents our team has requested from you

### Version Control
- Track document versions and changes over time
- Roll back to previous versions if needed
- View upload and modification history

---

##  Project Updates & Activity Feed

Providers can add dated comments and updates to keep you informed. This creates a living history of your project -- a clear, chronological record of what's been done, what's happening, and what's next.

### How It Works
- **Add Updates** -- Providers can post dated comments with progress notes, decisions made, next steps, or any relevant information
- **View History** -- See a complete chronological timeline of all project activity
- **Stay Informed** -- Know exactly where things stand without needing to ask
- **Historical Record** -- Access past updates to understand the full story of your project

### What Gets Logged
- Progress updates from the provider
- Key decisions
- Document submissions and reviews
- Status changes
- Important discussions and decisions

---

##  Shared Documents & Resources

Access all documents that are due or relevant to you.

### Deliverables
- **Drafts** -- Work-in-progress documents for your review
- **Final Versions** -- Finalized deliverables
- **Templates** -- Standardized forms and templates used in your project
- **Contracts & Agreements** -- Signed agreements, NDAs, and SOWs

### Resource Library
- **Industry Reports** -- Research and benchmarking documents
- **Best Practice Guides** -- Engineering and operational best practices
- **Training Materials** -- Educational content related to your project
- **Regulatory References** -- Applicable standards and compliance documents

### Document Sharing
- **Share with Team** -- Grant access to specific documents with individual clients or internal team members
- **Download & Export** -- Download documents in multiple formats
- **Comments & Annotations** -- Leave feedback directly on shared documents

---

## Customer Reviews & Ratings

After project completion, clients can leave reviews and ratings for the consultancy. These reviews are submitted for provider approval before appearing on the public marketing landing page.

### How It Works
- **Post-Project Review** -- Once a project status changes to **Completed**, the client receives an invitation to leave a review
- **Rating Scale** -- 1 to 5 stars based on overall satisfaction
- **Written Feedback** -- Optional detailed comments about the experience
- **Provider Approval** -- All reviews are reviewed and approved/rejected by the provider before going live
- **Landing Page Display** -- Approved reviews appear on the public marketing landing page to showcase client satisfaction

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

##  Security & Privacy

Your data is protected with enterprise-grade security:

- **Encrypted Transmissions** -- All data is encrypted in transit (TLS 1.3)
- **Encrypted Storage** -- Files and data are encrypted at rest (AES-256)
- **Role-Based Access Control** -- Only authorized personnel can access specific information
- **Two-Factor Authentication (2FA)** -- Optional additional layer of security
- **Audit Logs** -- Full history of access and modifications
- **GDPR & Data Protection Compliance** -- Your data privacy is our priority

---

##  Sitemap

The portal is structured into **three distinct views** based on user roles. Each view provides access to specific features and pages tailored to the user's responsibilities.

```
+-----------------------------------------------------------------------------+
|                           CLIENT PORTAL SITEMAP                             |
+------------------+--------------------------+-------------------------------+
|   CUSTOMER       |   SERVICE PROVIDER       |        ADMIN                  |
|   (Client View)  |   (Non-Admin View)       |     (Provider Admin View)     |
+------------------+--------------------------+-------------------------------+
| * Dashboard      | * Dashboard              | * Dashboard                   |
| * My Projects    | * All Projects           | * All Projects                |
|   +- Drill-down  |   +- Drill-down          |   +- Drill-down               |
| * Documents      | * My Tasks               | * Documents                   |
| * Messages       | * Documents              | * Communication Center        |
| * Announcements  | * Messages               | * Announcements               |
| * Settings       | * Announcements          | * User Management             |
|                  | * Settings               | * Company Settings            |
|                  |                          |   +- Company Profile          |
|                  |                          |   +- Team Management        |
|                  |                          |   +- Role & Permission Management |
|                  |                          | * Service Catalog Management        |
|                  |                          | * Project Configuration       |
|                  |                          |   +- Dropdown Value Management|
|                  |                          | * System Settings             |
|                  |                          | * Audit Logs                  |
+------------------+--------------------------+-------------------------------+
| Global:  Notification Center (bell icon in header, available in all views) |
+-----------------------------------------------------------------------------+
```

####  Notification Center (Global)

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
| **Documents** | View, upload, and manage project documents -- shared files and requested documents |
| **Messages** | Participate in the project group conversation with your company's team and the consultancy team |
| **Announcements** | View official updates from the consultancy team |
| **Settings** | Account settings, personal information, password, 2FA configuration, notification preferences, team member invitations, **company profile** (company name, address, contact details, business type, client team members) |

#### Project Detail -- Drill-down (from My Projects)

*Accessible by clicking on any project from the My Projects list.*

| Section | Description |
|---------|-------------|
| **Overview** | Project scope, objectives, status, service type, dated updates & activity feed (chronological record of what's been done and what's next) |
| **Documents** | Project-specific documents -- shared files and requested documents |
| **Messages** | Project-specific group conversation with your company's team and the consultancy team |

### 2. Service Provider View (Non-Admin)

*Accessible to consultants, project managers, and team members who are not administrators.*

| Page | Description |
|------|-------------|
| **Dashboard** | Overview of recent tasks, active projects, pending messages, and latest project updates |
| **All Projects** | Complete list of all projects the provider has access to, with filtering and search. Clicking a project drills down into the **Project Detail** page (see below). **New Project** button to open the project creation wizard |
| **My Tasks** | View and manage individual tasks assigned to the provider across all projects -- due dates, status, priority |
| **Documents** | View, upload, comment on, and manage project documents -- deliverables, client submissions, version control |
| **Messages** | Participate in project group conversations with clients and team members |
| **Announcements** | Create and publish project/company announcements |
| **Settings** | Personal information, notification preferences, communication settings |

#### Project Detail -- Drill-down (from All Projects)

*Accessible by clicking on any project from the All Projects list.*

| Section | Description |
|---------|-------------|
| **Overview** | Project scope, objectives, status, assigned team members, service type, dated updates & activity feed (chronological record of what's been done and what's next) |
| **Client Company & Team** | Client company information, authorized representative details, and assigned team members |
| **Documents** | Project-specific documents -- deliverables, client submissions, version control |
| **Messages** | Project-specific group conversation with clients and team members |

### 3. Admin View (Provider Administrator)

*Accessible to system administrators with full access to all portal features.*

| Page | Description |
|------|-------------|
| **Dashboard** | System-wide overview -- total clients, active projects, revenue metrics, system health |
| **All Projects** | Complete list of all projects across all clients with filtering and search. Clicking a project drills down into the **Project Detail** page (see below). **New Project** button to open the project creation wizard |
| **Documents** | Centralized document repository with advanced search, version control, and compliance tracking |
| **User Management** | Manage client accounts -- create, edit, deactivate client users; assign to projects |
| **Company Settings** | Parent section for all company-related configuration (see sub-pages below) |
| | **Company Profile** | Configure consultancy/company profile -- business details, branding, contact information, operational data fields |
| | **Team Management** | Manage internal provider/staff accounts -- create, edit, deactivate team members; assign roles, projects, and permissions |
| | **Role & Permission Management** | Configure custom roles and permission sets for all user types -- clients, service providers (staff), and administrators; define granular access controls per role |
| **Service Catalog Management** | Create, update, and archive services offered to clients |
| **Project Configuration** | Set up project templates, define standard workflows, manage project statuses (add/edit/delete statuses, colors, descriptions, transition rules) |
| **Reviews & Ratings** | Manage customer reviews -- approve/reject submitted reviews, manage approved reviews on landing page |
| **Communication Center** | System-wide announcements, communication logs |
| **System Settings** | General portal configuration, email templates, integrations, security policies |
| **Audit Logs** | Track all user actions, system changes, and access events |

#### Project Detail -- Drill-down (from All Projects)

*Accessible by clicking on any project from the All Projects list.*

| Section | Description |
|---------|-------------|
| **Overview** | Full project view -- scope, objectives, status, assigned team members, client company information, service type, dated updates & activity feed (chronological record of what's been done and what's next) |
| **Client Company & Team** | Client company information, authorized representative details, assigned consultant team |
| **Documents** | Project-specific documents -- centralized repository with version control |
| **Messages** | Project-specific group conversation with clients and team members |
| **Admin Controls** | Full administrative control -- reassign team, modify scope, adjust timelines, change status |

#### Company Settings -- Sub-pages

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
| View own projects |  |  (assigned only) |  (all) |
| View all projects |  |  |  |
| Upload documents |  |  |  |
| Request documents from clients |  |  |  |
| Manage users |  |  |  |
| Configure roles/permissions |  |  |  |
| Manage service catalog |  |  |  |
| View assigned service type |  |  |  |
| System settings |  |  |  |
| View analytics | Limited | Project-level | System-wide |
| Manage announcements |  |  |  |
| Audit logs |  |  |  |

---
##  Field Mapping -- All Pages

Detailed field-by-field mapping for every page in the portal, organized by view.

---

### 0. Account Creation / Onboarding

| Field | Section | Type | Required | Source |
|-------|---------|------|----------|--------|
| Full Name | Authorized Representative | text |  | User input |
| Job Title | Authorized Representative | text |  | User input |
| Email Address | Authorized Representative | email |  | User input |
| Phone Number | Authorized Representative | tel |  | User input |
| Company Name | Company Overview | text |  | User input |
| Location | Company Overview | text |  | User input |
| Company Owner | Company Overview | text |  | User input |
| Business Type / Description | Company Overview | textarea |  | User input |
| Total Project Cost | Project & Production Details | currency |  | User input |
| Raw Materials | Raw Materials | table (name, tons) |  | User input |
| Output in Tons per Year | Production Output | table (product, tons) |  | User input |
| Amount of Product/Output per Year | Production Output | table (monthly, yearly) |  | User input |
| Waste Management Practices | Waste Management | textarea |  | User input |
| Amount of Waste Material per Month | Waste Management | table (type, tons) |  | User input |
| Manufacturing Procedure | Manufacturing Process | textarea |  | User input |
| Production Flowchart | Manufacturing Process | file upload |  | User upload (PDF, PNG, JPG, SVG) |

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

#### Project Detail -- Overview

| Field | Type | Description |
|-------|------|-------------|
| Project Name | text | Project title |
| Scope | textarea | Project scope |
| Objectives | textarea | Goals and objectives |
| Status | badge | Current status |
| Service Type | text | Assigned service |
| Recent Updates | timeline | Latest dated comments from the provider |
| Update History | timeline | Full chronological record of all updates |

#### Project Detail -- Documents

| Field | Type | Description |
|-------|------|-------------|
| Document Name | text | File title |
| Category | **dropdown** (static, managed) | Client-Submitted / Requested |
| File | file | PDF, DOCX, XLSX, PNG, JPG, DWG, STEP, IGES |
| Version | text | Current version |
| Description | textarea | Optional notes |

#### Project Detail -- Messages

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

####  Notification Center (all views)

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

#### Project Detail -- Overview

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

#### Project Detail -- Client Company & Team

| Field | Type | Description |
|-------|------|-------------|
| Company Name | text | Client company |
| Company Address | text | Client address |
| Business Type | text | Client industry |
| Authorized Representative | text | Project rep |
| Assigned Team | table | Provider team members |

#### Project Detail -- Documents

| Field | Type | Description |
|-------|------|-------------|
| Document Name | text | File title |
| Category | **dropdown** (static, managed) | Deliverable / Client-Submitted / Requested |
| File | file | Attached file |
| Version | text | Version number |
| Comments | textarea | Document feedback |

#### Project Detail -- Messages

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

#### Project Detail -- Overview

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

#### Project Detail -- Client Company & Team

| Field | Type | Description |
|-------|------|-------------|
| Company Name | text | Client company |
| Company Address | text | Client address |
| Business Type | text | Client industry |
| Authorized Representative | text | Project rep |
| Assigned Consultant Team | table | Provider team |

#### Project Detail -- Documents

| Field | Type | Description |
|-------|------|-------------|
| Document Name | text | File title |
| Category | **dropdown** (static, managed) | Document type |
| File | file | Attached file |
| Version | text | Version number |

#### Project Detail -- Messages

| Field | Type | Description |
|-------|------|-------------|
| Group Conversation | list | Project-wide shared thread |
| Sender | text | Participant |
| Timestamp | date/time | Message time |
| Reply Box | textarea | Compose message |

#### Project Detail -- Admin Controls

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

#### Company Settings -- Company Profile

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

#### Company Settings -- Team Management

| Field | Type | Description |
|-------|------|-------------|
| Staff Name | text | Team member name |
| Email | email | Login email |
| Role | **dropdown** (static, managed) | Team role |
| Assigned Projects | multi-select (dynamic, projects) | Project access |
| Permissions | checkboxes | Granular perms |
| Status | **dropdown** (static, managed) | Active / Deactivated |

#### Company Settings -- Role & Permission Management

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
| Project Status | Not Started / In Progress / On Hold / Completed |  |
| Document Category | Client-Submitted / Requested |  |
| Announcement Category | Project Update / Company News / Maintenance |  |
| Task Status | To Do / In Progress / Done |  |
| Priority | Low / Medium / High |  |
| User Role | Client / Provider / Admin |  |
| Service Category | (from Service Catalog) |  |
| Audience | Project / Company |  |
| Industry Sector | (custom, populated by admin) |  |
| Report Type | Performance / Satisfaction / Resources / Revenue |  |
| Status | Active / Deactivated / Archived |  |

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
|| Details | text | Change details |

---

##  Database Schema -- PostgreSQL

The backend uses PostgreSQL 17 as the relational data store. All tables follow standard naming conventions (snake_case, plural entity names). Every table includes `created_at` and `updated_at` timestamps. The `id` column is a `BIGSERIAL` primary key unless otherwise noted.

### Entity Relationship Overview

```
users
  |
  +-- companies (authorized_rep_user_id -> users.id)
  |
  +-- project_team_members (user_id -> users.id)
  |
  +-- tasks (assignee_id -> users.id)
  |
  +-- messages (sender_id -> users.id)
  |
  +-- notifications (recipient_id -> users.id)
  |
  +-- reviews (customer_user_id -> users.id)
  |
  +-- audit_logs (user_id -> users.id)

projects
  |
  +-- project_team_members (project_id -> projects.id)
  +-- tasks (project_id -> projects.id)
  +-- documents (project_id -> projects.id)
  +-- messages (project_id -> projects.id)
  +-- reviews (project_id -> projects.id)

companies
  |
  +-- projects (company_id -> companies.id)

services
  |
  +-- projects (service_id -> services.id)

dropdown_categories
  |
  +-- dropdown_values (category_id -> dropdown_categories.id)
```

---

### Core Tables

#### `users`

User accounts for all portal roles (Client, Provider, Admin). Supports email/password and SSO (Google, Microsoft, LinkedIn).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL | |
| `password_hash` | VARCHAR(255) | | NULL for SSO-only users |
| `first_name` | VARCHAR(100) | NOT NULL | |
| `last_name` | VARCHAR(100) | NOT NULL | |
| `role` | VARCHAR(20) | NOT NULL | `CLIENT`, `PROVIDER`, `ADMIN` |
| `is_active` | BOOLEAN | DEFAULT TRUE | |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |
| `last_login` | TIMESTAMP | | |

#### `companies`

Client company profiles. Each company has one authorized representative (a user).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `name` | VARCHAR(255) | NOT NULL | |
| `location` | VARCHAR(255) | | |
| `owner` | VARCHAR(255) | | |
| `description` | TEXT | | Business type / description |
| `authorized_rep_user_id` | BIGINT | NOT NULL, REFERENCES users(id) | |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |

#### `roles`

Custom role definitions for provider internal permission management.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `name` | VARCHAR(100) | UNIQUE, NOT NULL | |
| `description` | TEXT | | |
| `is_system` | BOOLEAN | DEFAULT FALSE | System roles cannot be deleted |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |

#### `permissions`

Granular permission definitions mapped to roles.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `name` | VARCHAR(100) | UNIQUE, NOT NULL | e.g., `project.view`, `user.manage` |
| `description` | TEXT | | |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |

#### `role_permissions`

Many-to-many between roles and permissions.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `role_id` | BIGINT | REFERENCES roles(id) | |
| `permission_id` | BIGINT | REFERENCES permissions(id) | |
| PRIMARY KEY | (role_id, permission_id) | | |

#### `user_roles`

Many-to-many between users and roles (overrides default role).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `user_id` | BIGINT | REFERENCES users(id) | |
| `role_id` | BIGINT | REFERENCES roles(id) | |
| `PRIMARY KEY` | (user_id, role_id) | | |

---

### Project Tables

#### `projects`

Projects link to a company and a service. Status tracked via dropdown category.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `company_id` | BIGINT | NOT NULL, REFERENCES companies(id) | |
| `service_id` | BIGINT | REFERENCES services(id) | Service type |
| `name` | VARCHAR(255) | NOT NULL | Project name |
| `scope` | TEXT | | Project scope description |
| `objectives` | TEXT | | |
| `deliverables` | TEXT | | |
| `status` | VARCHAR(30) | DEFAULT 'NOT_STARTED' | From dropdown: `NOT_STARTED`, `IN_PROGRESS`, `ON_HOLD`, `COMPLETED` |
| `total_cost` | DECIMAL(15,2) | | |
| `raw_materials` | JSONB | | Array of {name, tons} |
| `production_output` | JSONB | Array of {product, tons} | |
| `waste_management` | TEXT | | |
| `waste_materials` | JSONB | Array of {type, tons} | |
| `manufacturing_procedure` | TEXT | | |
| `production_flowchart_url` | VARCHAR(500) | | File URL |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |

#### `project_team_members`

Maps users to projects (both provider staff and client contacts).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `project_id` | BIGINT | REFERENCES projects(id) | |
| `user_id` | BIGINT | REFERENCES users(id) | |
| `role_on_project` | VARCHAR(50) | NOT NULL | `PROJECT_MANAGER`, `ENGINEER`, `CLIENT_REP`, etc. |
| `assigned_at` | TIMESTAMP | DEFAULT NOW() | |
| PRIMARY KEY | (project_id, user_id) | | |

---

### Task & Document Tables

#### `tasks`

Individual work items within a project.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `project_id` | BIGINT | NOT NULL, REFERENCES projects(id) | |
| `assignee_id` | BIGINT | REFERENCES users(id) | |
| `title` | VARCHAR(255) | NOT NULL | |
| `description` | TEXT | | |
| `status` | VARCHAR(30) | DEFAULT 'TO_DO' | From dropdown: `TO_DO`, `IN_PROGRESS`, `DONE` |
| `priority` | VARCHAR(20) | DEFAULT 'MEDIUM' | From dropdown: `LOW`, `MEDIUM`, `HIGH` |
| `due_date` | DATE | | |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |

#### `documents`

Project documents with version tracking and category classification.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `project_id` | BIGINT | NOT NULL, REFERENCES projects(id) | |
| `uploader_id` | BIGINT | REFERENCES users(id) | |
| `title` | VARCHAR(255) | NOT NULL | |
| `description` | TEXT | | |
| `category` | VARCHAR(50) | DEFAULT 'OTHER' | From dropdown: `CLIENT_SUBMITTED`, `REQUESTED`, `DELIVERABLE`, etc. |
| `file_url` | VARCHAR(500) | NOT NULL | |
| `file_size` | BIGINT | | Bytes |
| `version` | INTEGER | DEFAULT 1 | |
| `is_latest` | BOOLEAN | DEFAULT TRUE | |
| `uploaded_at` | TIMESTAMP | DEFAULT NOW() | |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |

#### `document_comments`

Comments on documents.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `document_id` | BIGINT | NOT NULL, REFERENCES documents(id) | |
| `user_id` | BIGINT | NOT NULL, REFERENCES users(id) | |
| `comment` | TEXT | NOT NULL | |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |

---

### Communication Tables

#### `messages`

Project group conversations. One thread per project.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `project_id` | BIGINT | NOT NULL, REFERENCES projects(id) | |
| `sender_id` | BIGINT | NOT NULL, REFERENCES users(id) | |
| `body` | TEXT | NOT NULL | |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |

#### `announcements`

Project or company-wide announcements.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `company_id` | BIGINT | REFERENCES companies(id) | NULL = company-wide |
| `project_id` | BIGINT | REFERENCES projects(id) | NULL = company-wide |
| `title` | VARCHAR(255) | NOT NULL | |
| `body` | TEXT | NOT NULL | |
| `category` | VARCHAR(30) | DEFAULT 'PROJECT_UPDATE' | From dropdown: `PROJECT_UPDATE`, `COMPANY_NEWS`, `MAINTENANCE` |
| `audience` | VARCHAR(20) | DEFAULT 'COMPANY' | `PROJECT` or `COMPANY` |
| `is_published` | BOOLEAN | DEFAULT TRUE | |
| `created_by` | BIGINT | NOT NULL, REFERENCES users(id) | |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |

---

### Review & Rating Tables

#### `reviews`

Client reviews submitted after project completion. Require provider approval.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `customer_user_id` | BIGINT | NOT NULL, REFERENCES users(id) | |
| `project_id` | BIGINT | NOT NULL, REFERENCES projects(id) | |
| `rating` | INTEGER | NOT NULL, CHECK (rating >= 1 AND rating <= 5) | 1-5 stars |
| `title` | VARCHAR(255) | NOT NULL | |
| `body` | TEXT | NOT NULL | |
| `status` | VARCHAR(20) | DEFAULT 'PENDING' | `PENDING`, `APPROVED`, `REJECTED` |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |

---

### Service & Configuration Tables

#### `services`

Service catalog entries (e.g., feasibility studies, process optimization).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `name` | VARCHAR(255) | NOT NULL | |
| `description` | TEXT | | |
| `category` | VARCHAR(50) | DEFAULT 'ENGINEERING' | From dropdown: `FEASIBILITY`, `OPTIMIZATION`, `DESIGN`, `AUDIT`, etc. |
| `is_active` | BOOLEAN | DEFAULT TRUE | |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |

#### `dropdown_categories`

Configurable dropdown list definitions (editable by admin).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `name` | VARCHAR(100) | UNIQUE, NOT NULL | e.g., `project_status`, `task_status`, `priority` |
| `description` | TEXT | | |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |

#### `dropdown_values`

Values within a dropdown category.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `category_id` | BIGINT | NOT NULL, REFERENCES dropdown_categories(id) | |
| `value` | VARCHAR(100) | NOT NULL | e.g., `IN_PROGRESS` |
| `display_label` | VARCHAR(100) | NOT NULL | e.g., `In Progress` |
| `sort_order` | INTEGER | DEFAULT 0 | |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |

---

### System Tables

#### `notifications`

In-app notification center entries.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `recipient_id` | BIGINT | NOT NULL, REFERENCES users(id) | |
| `title` | VARCHAR(255) | NOT NULL | |
| `body` | TEXT | NOT NULL | |
| `type` | VARCHAR(30) | NOT NULL | `TASK_ASSIGNED`, `PROJECT_CREATED`, `MESSAGE`, `DOCUMENT_REQUEST`, `REVIEW_SUBMITTED`, `ANNOUNCEMENT`, etc. |
| `entity_type` | VARCHAR(50) | | Related entity (e.g., `project`, `task`) |
| `entity_id` | BIGINT | | Related entity ID |
| `is_read` | BOOLEAN | DEFAULT FALSE | |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |

#### `notification_preferences`

Per-user notification settings.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `user_id` | BIGINT | PRIMARY KEY, REFERENCES users(id) | |
| `task_assigned` | BOOLEAN | DEFAULT TRUE | |
| `project_created` | BOOLEAN | DEFAULT TRUE | |
| `new_message` | BOOLEAN | DEFAULT TRUE | |
| `document_request` | BOOLEAN | DEFAULT TRUE | |
| `review_submitted` | BOOLEAN | DEFAULT TRUE | |
| `announcement` | BOOLEAN | DEFAULT TRUE | |
| `status_change` | BOOLEAN | DEFAULT TRUE | |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |

#### `audit_logs`

Immutable audit trail for all significant actions.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `user_id` | BIGINT | REFERENCES users(id) | Actor |
| `action` | VARCHAR(100) | NOT NULL | e.g., `USER_CREATED`, `PROJECT_UPDATED` |
| `entity_type` | VARCHAR(50) | NOT NULL | e.g., `user`, `project` |
| `entity_id` | BIGINT | | Affected resource ID |
| `ip_address` | VARCHAR(45) | | IPv4 or IPv6 |
| `details` | JSONB | | Change diff or context |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |

#### `system_settings`

Portal-wide configuration.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `portal_name` | VARCHAR(255) | DEFAULT 'Client Portal' | |
| `email_templates` | JSONB | | Custom email template overrides |
| `integrations` | JSONB | | Third-party integration config |
| `security_policies` | JSONB | | Password policy, session timeout, etc. |
| `maintenance_mode` | BOOLEAN | DEFAULT FALSE | |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |

---

### Indexes

Standard indexes for query performance:

| Table | Index | Column(s) |
|-------|-------|-----------|
| `users` | idx_users_email | `email` (also UNIQUE constraint) |
| `users` | idx_users_role | `role` |
| `projects` | idx_projects_company | `company_id` |
| `projects` | idx_projects_status | `status` |
| `projects` | idx_projects_service | `service_id` |
| `tasks` | idx_tasks_project | `project_id` |
| `tasks` | idx_tasks_assignee | `assignee_id` |
| `tasks` | idx_tasks_status | `status` |
| `documents` | idx_documents_project | `project_id` |
| `messages` | idx_messages_project | `project_id` |
| `messages` | idx_messages_created | `created_at` |
| `notifications` | idx_notifications_recipient | `recipient_id` |
| `notifications` | idx_notifications_unread | `recipient_id`, `is_read` |
| `audit_logs` | idx_audit_user | `user_id` |
| `audit_logs` | idx_audit_created | `created_at` |
| `reviews` | idx_reviews_project | `project_id` |
| `reviews` | idx_reviews_status | `status` |
| `dropdown_values` | idx_dropdown_values_category | `category_id` |

---

### Default Data

#### `dropdown_categories` and `dropdown_values` (seed data)

| Category | Value | Display Label | Sort |
|----------|-------|---------------|------|
| `project_status` | `NOT_STARTED` | Not Started | 1 |
| `project_status` | `IN_PROGRESS` | In Progress | 2 |
| `project_status` | `ON_HOLD` | On Hold | 3 |
| `project_status` | `COMPLETED` | Completed | 4 |
| `task_status` | `TO_DO` | To Do | 1 |
| `task_status` | `IN_PROGRESS` | In Progress | 2 |
| `task_status` | `DONE` | Done | 3 |
| `priority` | `LOW` | Low | 1 |
| `priority` | `MEDIUM` | Medium | 2 |
| `priority` | `HIGH` | High | 3 |
| `document_category` | `CLIENT_SUBMITTED` | Client-Submitted | 1 |
| `document_category` | `REQUESTED` | Requested | 2 |
| `document_category` | `DELIVERABLE` | Deliverable | 3 |
| `announcement_category` | `PROJECT_UPDATE` | Project Update | 1 |
| `announcement_category` | `COMPANY_NEWS` | Company News | 2 |
| `announcement_category` | `MAINTENANCE` | Maintenance | 3 |
| `audience` | `PROJECT` | Project | 1 |
| `audience` | `COMPANY` | Company | 2 |
| `service_category` | `FEASIBILITY` | Feasibility Study | 1 |
| `service_category` | `OPTIMIZATION` | Process Optimization | 2 |
| `service_category` | `DESIGN` | Engineering Design | 3 |
| `service_category` | `AUDIT` | Compliance Audit | 4 |
| `user_role` | `CLIENT` | Client | 1 |
| `user_role` | `PROVIDER` | Provider | 2 |
| `user_role` | `ADMIN` | Admin | 3 |
| `report_type` | `PERFORMANCE` | Performance | 1 |
| `report_type` | `SATISFACTION` | Satisfaction | 2 |
| `report_type` | `RESOURCES` | Resources | 3 |
| `report_type` | `REVENUE` | Revenue | 4 |
| `status` (general) | `ACTIVE` | Active | 1 |
| `status` (general) | `DEACTIVATED` | Deactivated | 2 |
| `status` (general) | `ARCHIVED` | Archived | 3 |

---

### Docker Volume Mapping

| Volume | Mount Path | Purpose |
|--------|-----------|---------|
| `uploads` | `/app/uploads` | File uploads (documents, flowcharts) |
| `postgres-data` | `/var/lib/postgresql/data` | PostgreSQL data directory |

---

##  API Reference

The backend exposes a RESTful API at `http://localhost:8080/api/v1`. All endpoints require authentication unless otherwise noted. Responses are JSON. The API follows standard HTTP conventions: `GET` for reads, `POST` for creates, `PUT` for full updates, `PATCH` for partial updates, `DELETE` for removals.

### Swagger / OpenAPI Documentation

The API includes a Swagger UI interface powered by SpringDoc OpenAPI 2. Accessible at `http://localhost:8080/swagger-ui.html` when the backend is running locally. The OpenAPI 3.0 specification is served at `http://localhost:8080/v3/api-docs` for code generation, client SDKs, and API testing tools.

Swagger UI provides:

- Interactive API documentation with per-endpoint "Try it out" functionality
- Request/response schema visualization mapped to the database schema
- Authentication via the `Authorization` header (Bearer token)
- Filterable endpoint list grouped by domain (Auth, Users, Companies, Projects, Tasks, Documents, Messages, Reviews, Services, Admin, System)
- JSON request/response examples for every endpoint
- Error response schemas (400, 401, 403, 404, 409, 422, 500)

To enable Swagger in production, set the environment variable:

```
SWAGGER_ENABLED=true
```

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/auth/register` | Email registration |
| `POST` | `/auth/login` | Email login |
| `POST` | `/auth/sso/google` | Google SSO callback |
| `POST` | `/auth/sso/microsoft` | Microsoft SSO callback |
| `POST` | `/auth/sso/linkedin` | LinkedIn SSO callback |
| `POST` | `/auth/2fa/enable` | Enable 2FA |
| `POST` | `/auth/2fa/disable` | Disable 2FA |
| `POST` | `/auth/2fa/verify` | Verify 2FA code on login |
| `POST` | `/auth/logout` | Invalidate session |
| `POST` | `/auth/refresh` | Refresh access token |

**Request -- Register / Login**
```json
{
  "email": "user@example.com",
  "password": "string",
  "fullName": "string",
  "jobTitle": "string",
  "phone": "string"
}
```

**Response -- Auth Token**
```json
{
  "accessToken": "jwt-string",
  "refreshToken": "jwt-string",
  "expiresIn": 3600,
  "tokenType": "Bearer",
  "requires2fa": false
}
```

**Headers**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

---

### Users & Profiles

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/users/me` | Get current user profile | All |
| `PUT` | `/users/me` | Update current user profile | All |
| `PATCH` | `/users/me/password` | Change password | All |
| `GET` | `/users` | List all users (paginated) | Admin |
| `GET` | `/users/{id}` | Get user by ID | Admin |
| `POST` | `/users` | Create user account | Admin |
| `PUT` | `/users/{id}` | Update user | Admin |
| `PATCH` | `/users/{id}/status` | Activate/deactivate user | Admin |
| `DELETE` | `/users/{id}` | Delete user | Admin |
| `POST` | `/users/{id}/invite` | Send invitation email | Admin, Provider |

**Request -- Create User**
```json
{
  "email": "user@example.com",
  "password": "string",
  "fullName": "string",
  "role": "CLIENT|PROVIDER|ADMIN",
  "companyId": "uuid",
  "assignedProjectIds": ["uuid"]
}
```

---

### Companies

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/companies` | List all companies | Admin |
| `GET` | `/companies/{id}` | Get company details | Admin, Provider (assigned), Client |
| `POST` | `/companies` | Create company (onboarding) | All (self-registration) |
| `PUT` | `/companies/{id}` | Update company | Admin, Client (own) |
| `GET` | `/companies/{id}/team` | Get company team members | Admin, Provider (assigned), Client |
| `POST` | `/companies/{id}/team` | Add team member | Admin, Client |
| `PUT` | `/companies/{id}/team/{userId}` | Update team member | Admin, Client |
| `DELETE` | `/companies/{id}/team/{userId}` | Remove team member | Admin, Client |

**Request -- Create Company (Onboarding)**
```json
{
  "name": "string",
  "location": "string",
  "owner": "string",
  "businessType": "string",
  "authorizedRepresentative": {
    "fullName": "string",
    "jobTitle": "string",
    "email": "string",
    "phone": "string"
  },
  "projectCost": 500000.00,
  "rawMaterials": [
    {"name": "Steel", "quantityTons": 100, "period": "MONTHLY|YEARLY"}
  ],
  "productionOutput": [
    {"productName": "Beams", "annualTons": 500}
  ],
  "productionBreakdown": [
    {"productName": "Beams", "monthlyTons": 42, "annualTons": 500}
  ],
  "wasteManagement": {
    "practices": "string",
    "monthlyWaste": [
      {"type": "Recyclable", "quantityTons": 10}
    ]
  },
  "manufacturingProcedure": "string",
  "productionFlowchart": "file (multipart)"
}
```

---

### Projects

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/projects` | List projects (paginated, filterable) | Admin (all), Provider (assigned), Client (assigned) |
| `GET` | `/projects/{id}` | Get project detail | All (assigned) |
| `POST` | `/projects` | Create project (wizard) | Admin, Provider |
| `PUT` | `/projects/{id}` | Update project | Admin, Provider |
| `PATCH` | `/projects/{id}/status` | Change project status | Admin, Provider |
| `DELETE` | `/projects/{id}` | Delete project | Admin |
| `GET` | `/projects/{id}/dashboard` | Project dashboard metrics | All (assigned) |
| `POST` | `/projects/{id}/team` | Assign team members | Admin, Provider |
| `DELETE` | `/projects/{id}/team/{userId}` | Remove team member | Admin, Provider |
| `GET` | `/projects/{id}/client-company` | Get linked company info | All (assigned) |

**Request -- Create Project (Wizard)**
```json
{
  "scenario": "NEW_CUSTOMER|EXISTING_CUSTOMER",
  "existingCompanyId": "uuid",
  "company": {
    "name": "string",
    "location": "string",
    "owner": "string",
    "businessType": "string"
  },
  "authorizedRepresentative": {
    "fullName": "string",
    "jobTitle": "string",
    "email": "string",
    "phone": "string"
  },
  "name": "string",
  "serviceTypeId": "uuid",
  "description": "string",
  "estimatedStartDate": "2026-09-01",
  "estimatedCompletionDate": "2026-12-31"
}
```

**Response -- Project**
```json
{
  "id": "uuid",
  "name": "string",
  "scope": "string",
  "objectives": "string",
  "status": "NOT_STARTED|IN_PROGRESS|ON_HOLD|COMPLETED",
  "serviceType": { "id": "uuid", "name": "string" },
  "clientCompany": { "id": "uuid", "name": "string" },
  "teamMembers": [{"id": "uuid", "name": "string", "role": "string"}],
  "estimatedStartDate": "2026-09-01",
  "estimatedCompletionDate": "2026-12-31",
  "createdAt": "2026-08-12T10:00:00Z",
  "updatedAt": "2026-08-12T10:00:00Z"
}
```

**Query Parameters -- List Projects**
```
?status=IN_PROGRESS&search=acme&page=0&size=20&sortBy=name&sortDir=asc
```

---

### Tasks

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/tasks` | List tasks (paginated, filterable) | All (assigned) |
| `GET` | `/tasks/{id}` | Get task detail | All (assigned) |
| `POST` | `/tasks` | Create task | Admin, Provider |
| `PUT` | `/tasks/{id}` | Update task | Admin, Provider |
| `PATCH` | `/tasks/{id}/status` | Change task status | All (assigned) |
| `DELETE` | `/tasks/{id}` | Delete task | Admin |

**Request -- Create Task**
```json
{
  "projectId": "uuid",
  "title": "string",
  "description": "string",
  "assigneeId": "uuid",
  "dueDate": "2026-09-15",
  "status": "TODO|IN_PROGRESS|DONE",
  "priority": "LOW|MEDIUM|HIGH"
}
```

---

### Documents

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/documents` | List documents (paginated, filterable) | All (assigned) |
| `GET` | `/documents/{id}` | Get document detail | All (assigned) |
| `POST` | `/documents` | Upload document (multipart) | All (assigned) |
| `PUT` | `/documents/{id}` | Update document metadata | All (assigned) |
| `DELETE` | `/documents/{id}` | Delete document | Admin |
| `GET` | `/documents/{id}/download` | Download file | All (assigned) |
| `GET` | `/documents/{id}/versions` | List document versions | All (assigned) |
| `POST` | `/documents/{id}/versions/{versionId}/restore` | Restore version | All (assigned) |
| `POST` | `/projects/{projectId}/documents/request` | Request document from client | Admin, Provider |
| `GET` | `/documents/{id}/comments` | Get document comments | All (assigned) |
| `POST` | `/documents/{id}/comments` | Add comment | All (assigned) |

**Query Parameters -- List Documents**
```
?projectId=uuid&category=CLIENT_SUBMITTED|REQUESTED&search=filename&page=0&size=20
```

**Response -- Document**
```json
{
  "id": "uuid",
  "name": "string",
  "category": "CLIENT_SUBMITTED|REQUESTED|DELIVERABLE",
  "version": "1.0",
  "description": "string",
  "tags": ["tag1", "tag2"],
  "fileUrl": "/api/v1/documents/{id}/download",
  "fileSize": 1048576,
  "mimeType": "application/pdf",
  "uploadedBy": { "id": "uuid", "name": "string" },
  "projectId": "uuid",
  "createdAt": "2026-08-12T10:00:00Z",
  "updatedAt": "2026-08-12T10:00:00Z"
}
```

**Request -- Request Document**
```json
{
  "projectId": "uuid",
  "description": "string",
  "requiredBy": "2026-09-01",
  "recipientIds": ["uuid"]
}
```

---

### Messages & Conversations

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/conversations` | List project conversations | All (assigned) |
| `GET` | `/conversations/{id}` | Get conversation messages | All (assigned) |
| `POST` | `/conversations/{id}/messages` | Send message | All (assigned) |
| `GET` | `/conversations/{id}/participants` | Get conversation participants | All (assigned) |
| `DELETE` | `/conversations/{id}/messages/{messageId}` | Delete message (own) | Message author, Admin |
| `GET` | `/conversations/{id}/unread-count` | Get unread count | All (assigned) |
| `POST` | `/conversations/{id}/read` | Mark all as read | All (assigned) |

**Request -- Send Message**
```json
{
  "content": "string",
  "attachments": [{"name": "string", "file": "multipart"}]
}
```

**Response -- Message**
```json
{
  "id": "uuid",
  "content": "string",
  "sender": { "id": "uuid", "name": "string" },
  "timestamp": "2026-08-12T10:00:00Z",
  "readBy": [{"id": "uuid", "name": "string", "readAt": "2026-08-12T10:05:00Z"}],
  "attachments": [{"name": "string", "url": "string"}],
  "replies": []
}
```

---

### Announcements

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/announcements` | List announcements (paginated) | All (assigned) |
| `GET` | `/announcements/{id}` | Get announcement detail | All (assigned) |
| `POST` | `/announcements` | Create announcement | Admin, Provider |
| `PUT` | `/announcements/{id}` | Update announcement | Admin, Provider (own) |
| `DELETE` | `/announcements/{id}` | Delete announcement | Admin |
| `POST` | `/announcements/{id}/read` | Mark as read | All (assigned) |

**Request -- Create Announcement**
```json
{
  "title": "string",
  "body": "string",
  "category": "PROJECT_UPDATE|COMPANY_NEWS|MAINTENANCE",
  "audience": "PROJECT|COMPANY",
  "projectId": "uuid",
  "publishDate": "2026-08-12"
}
```

---

### Reviews & Ratings

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/reviews` | List all reviews (admin) | Admin |
| `GET` | `/reviews/my` | List my submitted reviews | Client |
| `GET` | `/reviews/{id}` | Get review detail | Reviewer, Admin |
| `POST` | `/reviews` | Submit review | Client (completed project) |
| `PUT` | `/reviews/{id}` | Update review (pending only) | Reviewer |
| `DELETE` | `/reviews/{id}` | Delete review (pending only) | Reviewer |
| `POST` | `/reviews/{id}/approve` | Approve review | Admin |
| `POST` | `/reviews/{id}/reject` | Reject review | Admin |
| `GET` | `/reviews/approved` | List approved reviews (public) | Public (landing page) |
| `GET` | `/reviews/aggregate` | Get aggregate rating | Public (landing page) |

**Request -- Submit Review**
```json
{
  "projectId": "uuid",
  "rating": 5,
  "title": "string",
  "body": "string",
  "wouldRecommend": true
}
```

**Response -- Review**
```json
{
  "id": "uuid",
  "customer": { "name": "string", "company": "string" },
  "project": { "id": "uuid", "name": "string", "serviceType": "string" },
  "rating": 5,
  "title": "string",
  "body": "string",
  "wouldRecommend": true,
  "status": "PENDING|APPROVED|REJECTED",
  "approvedDate": "2026-08-12T10:00:00Z",
  "createdAt": "2026-08-12T10:00:00Z"
}
```

**Response -- Aggregate Rating**
```json
{
  "averageRating": 4.6,
  "reviewCount": 12,
  "ratingDistribution": { "1": 0, "2": 1, "3": 2, "4": 4, "5": 5 }
}
```

---

### Notifications

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/notifications` | List notifications (paginated, newest first) | All |
| `GET` | `/notifications/unread-count` | Get unread count | All |
| `PATCH` | `/notifications/{id}/read` | Mark single as read | Owner |
| `POST` | `/notifications/read-all` | Mark all as read | All |
| `GET` | `/notifications/preferences` | Get notification preferences | All |
| `PUT` | `/notifications/preferences` | Update notification preferences | All |

**Response -- Notification**
```json
{
  "id": "uuid",
  "type": "MESSAGE|DOCUMENT|TASK|PROJECT|ANNOUNCEMENT",
  "title": "string",
  "message": "string",
  "targetUrl": "/projects/{id}",
  "read": false,
  "createdAt": "2026-08-12T10:00:00Z"
}
```

**Request -- Notification Preferences**
```json
{
  "email": {
    "projectCreated": true,
    "newMessage": true,
    "projectUpdate": true,
    "documentUploaded": true,
    "documentRequested": true,
    "taskAssigned": true,
    "taskStatusChanged": true,
    "projectStatusChanged": true,
    "announcement": true,
    "teamInvitation": true
  },
  "inApp": {
    "newMessage": true,
    "documentUploaded": true,
    "taskAssigned": true,
    "projectStatusChanged": true,
    "announcement": true
  }
}
```

---

### Service Catalog

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/services` | List all services | All |
| `GET` | `/services/{id}` | Get service detail | All |
| `POST` | `/services` | Create service | Admin |
| `PUT` | `/services/{id}` | Update service | Admin |
| `DELETE` | `/services/{id}` | Archive service | Admin |

**Request -- Create Service**
```json
{
  "name": "string",
  "description": "string",
  "category": "string",
  "status": "ACTIVE|ARCHIVED",
  "price": 5000.00
}
```

---

### Project Configuration

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/config/templates` | List project templates | Admin |
| `GET` | `/config/templates/{id}` | Get template detail | Admin |
| `POST` | `/config/templates` | Create template | Admin |
| `PUT` | `/config/templates/{id}` | Update template | Admin |
| `DELETE` | `/config/templates/{id}` | Delete template | Admin |
| `GET` | `/config/statuses` | List project statuses | Admin |
| `POST` | `/config/statuses` | Create status | Admin |
| `PUT` | `/config/statuses/{id}` | Update status | Admin |
| `DELETE` | `/config/statuses/{id}` | Delete status | Admin |

**Request -- Create Status**
```json
{
  "name": "string",
  "color": "#4CAF50",
  "description": "string",
  "transitionRules": [
    {"from": "NOT_STARTED", "to": "IN_PROGRESS"},
    {"from": "IN_PROGRESS", "to": "COMPLETED"}
  ]
}
```

---

### Dropdown Value Management

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/dropdowns` | List all dropdown categories | Admin |
| `GET` | `/dropdowns/{category}` | Get values for category | All |
| `POST` | `/dropdowns/{category}` | Add value | Admin |
| `PUT` | `/dropdowns/{category}/{valueId}` | Update value | Admin |
| `DELETE` | `/dropdowns/{category}/{valueId}` | Delete value | Admin |
| `PATCH` | `/dropdowns/{category}/{valueId}/order` | Reorder values | Admin |
| `PATCH` | `/dropdowns/{category}/{valueId}/active` | Toggle active | Admin |

**Request -- Add Dropdown Value**
```json
{
  "value": "string",
  "color": "#FF5722",
  "description": "string",
  "sortOrder": 1,
  "active": true
}
```

---

### Company Settings (Consultancy)

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/company/profile` | Get consultancy profile | Admin |
| `PUT` | `/company/profile` | Update consultancy profile | Admin |
| `POST` | `/company/profile/logo` | Upload logo (multipart) | Admin |

**Request -- Update Company Profile**
```json
{
  "name": "string",
  "tagline": "string",
  "description": "string",
  "industrySectors": ["Manufacturing", "Energy"],
  "address": {
    "street": "string",
    "city": "string",
    "state": "string",
    "country": "string",
    "postalCode": "string"
  },
  "phone": "string",
  "emails": {"general": "string", "support": "string", "billing": "string"},
  "website": "string",
  "socialMedia": {"linkedin": "string", "twitter": "string"},
  "taxNumber": "string",
  "brandColors": {"primary": "#1976D2", "secondary": "#FF5722"},
  "operationalDataFields": ["rawMaterials", "productionOutput", "wasteManagement"]
}
```

---

### Team Management (Internal Staff)

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/team` | List all team members | Admin |
| `GET` | `/team/{id}` | Get team member detail | Admin |
| `POST` | `/team` | Create team member | Admin |
| `PUT` | `/team/{id}` | Update team member | Admin |
| `PATCH` | `/team/{id}/status` | Activate/deactivate | Admin |
| `DELETE` | `/team/{id}` | Delete team member | Admin |

**Request -- Create Team Member**
```json
{
  "email": "staff@example.com",
  "password": "string",
  "fullName": "string",
  "role": "PROVIDER|ADMIN",
  "assignedProjectIds": ["uuid"],
  "permissions": ["manage_projects", "upload_documents", "manage_announcements"]
}
```

---

### Role & Permission Management

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/roles` | List all roles | Admin |
| `GET` | `/roles/{id}` | Get role detail | Admin |
| `POST` | `/roles` | Create role | Admin |
| `PUT` | `/roles/{id}` | Update role | Admin |
| `DELETE` | `/roles/{id}` | Delete role | Admin |

**Request -- Create Role**
```json
{
  "name": "string",
  "userType": "CLIENT|PROVIDER|ADMIN",
  "description": "string",
  "permissions": {
    "viewOwnProjects": true,
    "viewAllProjects": false,
    "uploadDocuments": true,
    "requestDocuments": true,
    "manageUsers": false,
    "configureRoles": false,
    "manageServiceCatalog": false,
    "manageAnnouncements": true,
    "viewAnalytics": false,
    "systemSettings": false,
    "auditLogs": false
  }
}
```

---

### System Settings

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/system/settings` | Get system settings | Admin |
| `PUT` | `/system/settings` | Update system settings | Admin |
| `GET` | `/system/email-templates` | List email templates | Admin |
| `GET` | `/system/email-templates/{key}` | Get email template | Admin |
| `PUT` | `/system/email-templates/{key}` | Update email template | Admin |
| `POST` | `/system/email-templates/{key}/test` | Send test email | Admin |

**Request -- Update System Settings**
```json
{
  "portalName": "string",
  "maintenanceMode": false,
  "securityPolicies": {
    "passwordMinLength": 12,
    "require2fa": false,
    "sessionTimeoutMinutes": 30,
    "maxLoginAttempts": 5
  },
  "integrations": {
    "emailProvider": "smtp",
    "videoCallProvider": "string"
  }
}
```

---

### Audit Logs

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/audit-logs` | List audit logs (paginated, filterable) | Admin |
| `GET` | `/audit-logs/{id}` | Get audit log entry | Admin |

**Query Parameters**
```
?userId=uuid&action=CREATE&entity=PROJECT&startDate=2026-01-01&endDate=2026-08-12&page=0&size=50
```

**Response -- Audit Log**
```json
{
  "id": "uuid",
  "timestamp": "2026-08-12T10:00:00Z",
  "user": { "id": "uuid", "name": "string" },
  "action": "CREATE|UPDATE|DELETE|LOGIN|LOGOUT",
  "entity": "PROJECT|DOCUMENT|USER|REVIEW|etc.",
  "entityId": "uuid",
  "ipAddress": "192.168.1.1",
  "details": "string"
}
```

---

### Dashboard & Analytics

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| `GET` | `/dashboard/client` | Client dashboard data | Client |
| `GET` | `/dashboard/provider` | Provider dashboard data | Provider |
| `GET` | `/dashboard/admin` | Admin dashboard data | Admin |

**Response -- Admin Dashboard**
```json
{
  "totalClients": 24,
  "activeProjects": 18,
  "revenueMetrics": {
    "totalRevenue": 2400000.00,
    "projectedRevenue": 3200000.00,
    "revenueByMonth": [{"month": "2026-08", "amount": 400000}]
  },
  "systemHealth": {
    "backend": "HEALTHY",
    "database": "HEALTHY",
    "lastBackup": "2026-08-12T02:00:00Z"
  },
  "recentActivity": [
    {"action": "Project completed", "user": "John Doe", "timestamp": "2026-08-12T10:00:00Z"}
  ]
}
```

---

### Error Responses

All endpoints return standard HTTP status codes:

| Code | Meaning |
|------|---------|
| `400` | Bad Request -- invalid input |
| `401` | Unauthorized -- missing or invalid token |
| `403` | Forbidden -- insufficient permissions |
| `404` | Not Found -- resource does not exist |
| `409` | Conflict -- duplicate resource |
| `413` | Payload Too Large -- file exceeds 100 MB |
| `422` | Unprocessable Entity -- validation error |
| `429` | Too Many Requests -- rate limited |
| `500` | Internal Server Error |

**Error Response Body**
```json
{
  "timestamp": "2026-08-12T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Email is already registered",
  "path": "/api/v1/auth/register"
}
```

---

### Pagination

All list endpoints support standard pagination:

```
GET /projects?page=0&size=20&sortBy=name&sortDir=asc
```

**Response -- Paginated**
```json
{
  "content": [...],
  "totalElements": 150,
  "totalPages": 8,
  "size": 20,
  "number": 0,
  "first": true,
  "last": false
}
```

---

### File Upload

Document uploads use `multipart/form-data`:

```
POST /documents
Content-Type: multipart/form-data

file: <binary>
name: "spec.pdf"
description: "Project specification"
tags: ["spec", "requirement"]
projectId: "uuid"
```

Max file size: **100 MB**. Supported MIME types: `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `image/png`, `image/jpeg`, `application/dwg`, `application/step`, `application/iges`.

---

##  Scaffolding Scope

This section documents the project scaffolding structure and setup plan for all three deployable components. It serves as a reference for the initial project creation before implementation begins.

### 1. `web/` -- Portal SPA (Frontend)

**Tech stack:** Vue 3 + TypeScript + Vite + Tailwind CSS + shadcn-vue + Vue Router + Pinia

**Directory structure:**

```
web/
├── src/
│   ├── components/           # Reusable shadcn-vue components
│   ├── composables/          # Vue composables (auth, notifications, etc.)
│   ├── layouts/              # DefaultLayout (sidebar+header), AuthLayout (centered card)
│   ├── router/               # Vue Router with role-based guards
│   ├── stores/               # Pinia stores (auth, projects, notifications)
│   ├── views/                # Page components:
│   │   ├── auth/             # Login, Register, SSO callback
│   │   ├── dashboard/        # Client, Provider, Admin dashboards
│   │   ├── projects/         # All Projects, Project Detail
│   │   ├── tasks/            # My Tasks
│   │   ├── documents/        # Documents list & detail
│   │   ├── messages/         # Project conversations
│   │   ├── announcements/    # Announcements list
│   │   ├── reviews/          # Submit review, review management
│   │   ├── settings/         # Profile, notification preferences
│   │   └── admin/            # Team, roles, settings, audit logs, dropdowns
│   ├── services/             # API client (Axios/fetch wrappers)
│   ├── utils/                # Helpers, formatters, validators
│   ├── App.vue
│   └── main.ts
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
├── tailwind.config.ts
├── components.json           # shadcn-vue config
├── Dockerfile
└── nginx.conf
```

**Initial setup tasks:**

- [ ] **S1.1** Create Vite project: `npx shadcn-vue@latest init --preset a2S74vUB --template vite --pointer`
- [ ] **S1.2** Install and configure Tailwind CSS v4
- [ ] **S1.3** Install dependencies: Vue Router, Pinia, Axios
- [ ] **S1.4** Configure Vue Router with placeholder routes for all pages (role-based guards)
- [ ] **S1.5** Set up Pinia with auth store (dummy data initially)
- [ ] **S1.6** Create base layout components (sidebar, header, auth card)
- [ ] **S1.7** Configure Tailwind theme (light/dark/system mode)
- [ ] **S1.8** Add Dockerfile and nginx.conf
- [ ] **S1.9** Verify build: `npm run build`

### 2. `landing/` -- Marketing Landing Page

**Tech stack:** Vue 3 + TypeScript + Vite + Tailwind CSS (no shadcn-vue needed)

**Directory structure:**

```
landing/
├── src/
│   ├── components/           # Hero, Features, Reviews, Footer
│   ├── views/
│   │   └── LandingPage.vue
│   ├── App.vue
│   └── main.ts
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
├── tailwind.config.ts
├── Dockerfile
└── nginx.conf
```

**Initial setup tasks:**

- [ ] **S2.1** Create Vite project: `npx shadcn-vue@latest init --preset a2S74vUB --template vite --pointer`
- [ ] **S2.2** Install and configure Tailwind CSS
- [ ] **S2.3** Create static landing page (hero, features, dummy reviews, footer)
- [ ] **S2.4** Add Dockerfile and nginx.conf
- [ ] **S2.5** Verify build: `npm run build`

### 3. Project Root Configuration

- [ ] **S3.1** Place `docker-compose.yml` at project root (as defined in Architecture section)
- [ ] **S3.2** Create `.env` file with documented environment variables
- [ ] **S3.3** Verify full stack builds: `docker compose build`

### Execution Order

```
S1 (web/ scaffold) --> S2 (landing/ scaffold) --> S3 (root config) --> Verify
```

The `web/` component is the larger piece and takes priority. Once scaffolding is complete, development can begin immediately on Phase 1 task **1.1.1 (Login Page)** using the established project structure.

---

##  Roadmap -- Implementation Plan

This section tracks all implementation tasks organized by layer. Tasks use checkboxes `[ ]` for pending and `[x]` for completed. **Frontend tasks are prioritized first** so the UI can be reviewed and validated early. Changes to the UI may cascade into API and database adjustments — those updates are noted in the relevant task descriptions.

### Phase 1: Frontend (UI First)

All frontend components use dummy/static data initially. Replace with API calls once the backend is ready.

#### 1.1 Authentication & Onboarding

- [x] **1.1.1** Login page (email/password + SSO buttons)
  - Layout: centered card, logo, email input, password input, "Remember me", "Forgot password?", login button
  - SSO buttons: Google, Microsoft, LinkedIn (dummy handlers for now)
  - 2FA verification modal (dummy)
  - Route: `/login`
  - *API impact:* None — frontend-only for now
  - **Status:** Scaffolded (`LoginView.vue`)

- [x] **1.1.2** Registration page (email/password)
  - Fields: email, password, confirm password, first name, last name
  - Password strength indicator
  - Route: `/register`
  - *API impact:* None — frontend-only for now
  - **Status:** Scaffolded (`RegisterView.vue`)

- [x] **1.1.3** SSO callback pages (Google, Microsoft, LinkedIn)
  - Dummy redirect to dashboard after "authentication"
  - Route: `/auth/sso/:provider/callback`
  - *API impact:* None — frontend-only for now
  - **Status:** Scaffolded (`SSOCallbackView.vue`)

- [x] **1.1.4** New Customer Onboarding Wizard
  - Step 1: Customer Company information (name, location, owner, description)
  - Step 2: Authorized Representative details (user creation form)
  - Step 3: Project Overview (name, scope, objectives, deliverables, service type dropdown)
  - Step 4: Finish (review summary, submit)
  - Route: `/wizard/new-customer`
  - *API impact:* Creates `POST /api/v1/companies`, `POST /api/v1/users`, `POST /api/v1/projects` endpoints
  - **Status:** Built (`NewProjectWizard.vue`, 533 lines, 4-step wizard with scenario selection)

#### 1.2 Navigation & Layout

- [x] **1.2.1** Main layout shell (sidebar + header + content area)
  - Sidebar: collapsible, role-based menu items
  - Header: user avatar, notifications bell, search bar
  - Route: `/` (base layout)
  - *API impact:* None — frontend-only for now
  - **Status:** Scaffolded (`MainLayout.vue`)

- [ ] **1.2.2** Sidebar menu by role
  - Client: Dashboard, My Projects, Messages, Documents, Reviews, Settings
  - Provider: Dashboard, All Projects, Team Management, Service Catalog, Announcements, Reviews, Settings
  - Admin: Dashboard, All Projects, Team Management, Role & Permission Management, System Settings, Audit Logs
  - *API impact:* None — frontend-only for now

#### 1.3 Dashboard Views

- [x] **1.3.1** Client Dashboard
  - Cards: Active Projects, Pending Tasks, Recent Messages, Upcoming Deadlines
  - Project status chart (dummy bar chart)
  - Recent activity feed (dummy timeline)
  - Route: `/dashboard/client`
  - *API impact:* Creates `GET /api/v1/dashboard/client` endpoint
  - **Status:** Scaffolded (`DashboardView.vue`)

- [ ] **1.3.2** Provider Dashboard
  - Cards: Total Projects, In Progress, Pending Reviews, Team Members
  - Project pipeline chart (dummy)
  - Recent announcements
  - Route: `/dashboard/provider`
  - *API impact:* Creates `GET /api/v1/dashboard/provider` endpoint

- [ ] **1.3.3** Admin Dashboard
  - Cards: Total Clients, Active Projects, Revenue Metrics, System Health
  - User growth chart (dummy)
  - System status indicators
  - Route: `/dashboard/admin`
  - *API impact:* Creates `GET /api/v1/dashboard/admin` endpoint

#### 1.4 Project Management

- [x] **1.4.1** All Projects list page
  - Table: project name, company, status, service type, team members, due date
  - Filters: status, service type, company
  - Search: by project name
  - Route: `/projects`
  - *API impact:* Creates `GET /api/v1/projects` with pagination and filters
  - **Status:** Scaffolded (`ProjectsView.vue`, 151 lines with wizard integration)

- [ ] **1.4.2** Project Detail page
  - Tabs: Overview, Tasks, Documents, Messages, Reviews, Team
  - Overview tab: project info cards, activity feed (dummy timeline)
  - *API impact:* Creates `GET /api/v1/projects/:id` endpoint

- [ ] **1.4.3** Project Overview tab
  - Fields: scope, objectives, status, service type, team members
  - JSON fields display: raw_materials, production_output, waste_materials
  - Production flowchart image placeholder
  - Activity feed (dummy)
  - *API impact:* None — part of project detail

- [x] **1.4.4** My Tasks page
  - Table: task title, project, assignee, status, priority, due date
  - Filters: status, priority, project
  - Route: `/tasks`
  - *API impact:* Creates `GET /api/v1/tasks` with filters
  - **Status:** Scaffolded (`TasksView.vue`)

- [x] **1.4.5** Task detail/edit modal
  - Fields: title, description, status, priority, due date, assignee
  - Route: `/tasks/:id`
  - *API impact:* Creates `PUT /api/v1/tasks/:id` endpoint
  - **Status:** Built (`TaskDetailModal.vue`, 286 lines)

#### 1.5 Communication Center

- [x] **1.5.1** Messages page (project conversations)
  - Chat interface: message list, input box, send button
  - Messages grouped by date
  - Online status indicator (dummy)
  - Route: `/projects/:id/messages`
  - *API impact:* Creates `GET /api/v1/projects/:id/messages`, `POST /api/v1/projects/:id/messages`
  - **Status:** Scaffolded (`MessagesView.vue`)

- [x] **1.5.2** Announcements page
  - List: announcement title, category, audience, date, published status
  - Create announcement form (admin/provider only): title, body, category, audience
  - Route: `/announcements`
  - *API impact:* Creates `GET /api/v1/announcements`, `POST /api/v1/announcements`
  - **Status:** Scaffolded (`AnnouncementsView.vue`)

#### 1.6 Document Management

- [x] **1.6.1** Documents page
  - Table: document title, category, uploader, date, version, file size
  - Filters: category, date range
  - Upload button (dummy file picker)
  - Route: `/projects/:id/documents`
  - *API impact:* Creates `GET /api/v1/documents`, `POST /api/v1/documents` (multipart)
  - **Status:** Scaffolded (`DocumentsView.vue`)

- [x] **1.6.2** Document detail view
  - File preview area (placeholder for PDF/image)
  - Comments section (dummy)
  - Version history table (dummy)
  - Route: `/projects/:id/documents/:id`
  - *API impact:* Creates `GET /api/v1/documents/:id`, `POST /api/v1/documents/:id/comments`
  - **Status:** Built (`DocumentDetailModal.vue`, 272 lines)

#### 1.7 Reviews & Ratings

- [x] **1.7.1** Reviews page (provider view)
  - Table: customer, project, rating, title, status, date
  - Actions: Approve, Reject, Edit
  - Route: `/reviews`
  - *API impact:* Creates `GET /api/v1/reviews`, `PUT /api/v1/reviews/:id`
  - **Status:** Scaffolded (`ReviewsView.vue`, 9.48 kB compiled)

- [x] **1.7.2** Submit review form (client view)
  - Fields: project (dropdown), rating (star selector), title, body
  - Route: `/projects/:id/reviews/new`
  - *API impact:* Creates `POST /api/v1/reviews`
  - **Status:** Built (`SubmitReviewForm.vue`, 235 lines)

- [x] **1.7.3** Public reviews display (marketing landing page)
  - Approved reviews grid: customer, project, rating, title, body
  - Route: `/reviews` (public)
  - *API impact:* Creates `GET /api/v1/reviews?status=APPROVED`
  - **Status:** Built (`ReviewsView.vue` public route)

#### 1.8 Admin Features

- [x] **1.8.1** Team Management page
  - Table: user, email, role, projects, status
  - Create user form: email, password, role, project assignments
  - Deactivate/reactivate toggle
  - Route: `/admin/team`
  - *API impact:* Creates `POST /api/v1/users`, `PUT /api/v1/users/:id`
  - **Status:** Scaffolded (`AdminView.vue`, 14.36 kB compiled)

- [ ] **1.8.2** Role & Permission Management page
  - Role list: name, description, permissions
  - Permission matrix table (checkboxes)
  - Create/edit role form
  - Route: `/admin/roles`
  - *API impact:* Creates `GET/POST/PUT /api/v1/roles`, `GET/POST/PUT /api/v1/permissions`

- [ ] **1.8.3** System Settings page
  - Fields: portal name, email templates (JSON editor), integrations (config form), security policies (form), maintenance mode toggle
  - Route: `/admin/settings`
  - *API impact:* Creates `GET/PUT /api/v1/settings`

- [ ] **1.8.4** Audit Logs page
  - Table: timestamp, user, action, entity, IP address, details
  - Filters: user, action, date range
  - Route: `/admin/audit-logs`
  - *API impact:* Creates `GET /api/v1/audit-logs`

- [ ] **1.8.5** Dropdown Configuration page
  - Category list: name, values
  - Edit category: add/remove values, reorder
  - Route: `/admin/dropdowns`
  - *API impact:* Creates `GET/POST/PUT/DELETE /api/v1/dropdowns/*`

#### 1.9 Settings & Profile

- [x] **1.9.1** User Profile page
  - Fields: first name, last name, email, avatar upload
  - Password change form
  - Route: `/settings/profile`
  - *API impact:* Creates `GET/PUT /api/v1/users/me`
  - **Status:** Scaffolded (`SettingsView.vue`, 8.24 kB compiled)

- [ ] **1.9.2** Notification Preferences page
  - Toggle switches: task assigned, project created, new message, document request, review submitted, announcement, status change
  - Route: `/settings/notifications`
  - *API impact:* Creates `GET/PUT /api/v1/users/me/notifications`

#### 1.10 Landing Page (Marketing)

- [x] **1.10.1** Marketing landing page (public)
  - Hero section: portal name, tagline, CTA buttons (Login, Register)
  - Features section: project management, communication, document sharing, reviews
  - Approved reviews section (dummy)
  - Footer: links, contact info
  - Route: `/` (public)
  - *API impact:* Creates `GET /api/v1/landing` (public, no auth)
  - **Status:** Built (`MarketingLandingPage.vue`, 277 lines, deployed and live at http://10.0.1.78:3000)

---

### Phase 2: Database

All database work uses the schema defined in the **Database Schema** section. Migrations are managed via Flyway (Java) or Liquibase.

- [ ] **2.1** PostgreSQL 17 Docker container setup
  - Docker Compose service definition
  - Environment variables: DB_USERNAME, DB_PASSWORD, DB_NAME
  - Volume: `postgres-data`
  - *Dependencies:* None

- [ ] **2.2** Initial schema migration (V1)
  - Create all tables: users, companies, roles, permissions, role_permissions, user_roles, projects, project_team_members, tasks, documents, document_comments, messages, announcements, reviews, services, dropdown_categories, dropdown_values, notifications, notification_preferences, audit_logs, system_settings
  - Create all indexes
  - *Dependencies:* PostgreSQL running

- [ ] **2.3** Seed data migration (V2)
  - Insert default dropdown categories and values (see Default Data table in Database Schema)
  - Insert default system roles (CLIENT, PROVIDER, ADMIN)
  - Insert default permissions
  - *Dependencies:* V1

- [ ] **2.4** Entity classes (JPA/Hibernate)
  - Create `@Entity` classes for all tables
  - Define relationships: `@OneToMany`, `@ManyToOne`, `@ManyToMany`
  - Configure `@Column`, `@Table`, `@Index` annotations
  - *Dependencies:* V1

- [ ] **2.5** Repository interfaces (Spring Data JPA)
  - Create `JpaRepository` interfaces for all entities
  - Define custom query methods where needed
  - *Dependencies:* Entity classes

---

### Phase 3: API (Backend)

Backend uses Spring Boot 3.x with Java 21. All endpoints follow REST conventions. Authentication uses JWT (access + refresh tokens).

- [ ] **3.1** Project setup and configuration
  - Spring Boot 3.x project with Java 21
  - Dependencies: Spring Web, Spring Data JPA, Spring Security, SpringDoc OpenAPI, Flyway, PostgreSQL driver, JWT library
  - `application.yml` configuration: database, JPA/Hibernate, JWT, Swagger
  - Dockerfile for backend
  - *Dependencies:* None

- [ ] **3.2** Authentication endpoints
  - `POST /auth/register` — email registration
  - `POST /auth/login` — email login (returns JWT)
  - `POST /auth/sso/google` — Google SSO callback
  - `POST /auth/sso/microsoft` — Microsoft SSO callback
  - `POST /auth/sso/linkedin` — LinkedIn SSO callback
  - `POST /auth/2fa/enable`, `POST /auth/2fa/disable`, `POST /auth/2fa/verify`
  - `POST /auth/logout` — invalidate session
  - `POST /auth/refresh` — refresh access token
  - *Dependencies:* Entity classes, Repository interfaces

- [ ] **3.3** User management endpoints
  - `GET /api/v1/users/me` — get current user
  - `PUT /api/v1/users/me` — update current user
  - `GET /api/v1/users` — list users (admin/provider)
  - `POST /api/v1/users` — create user (admin/provider)
  - `PUT /api/v1/users/:id` — update user (admin/provider)
  - `DELETE /api/v1/users/:id` — deactivate user (admin/provider)
  - *Dependencies:* Authentication

- [ ] **3.4** Company endpoints
  - `GET /api/v1/companies` — list companies (admin/provider)
  - `POST /api/v1/companies` — create company
  - `GET /api/v1/companies/:id` — get company detail
  - `PUT /api/v1/companies/:id` — update company
  - *Dependencies:* Entity classes, Repository interfaces

- [ ] **3.5** Project endpoints
  - `GET /api/v1/projects` — list projects (with pagination, filters)
  - `POST /api/v1/projects` — create project
  - `GET /api/v1/projects/:id` — get project detail
  - `PUT /api/v1/projects/:id` — update project
  - `DELETE /api/v1/projects/:id` — archive project
  - *Dependencies:* Company endpoints, Service endpoints

- [ ] **3.6** Team member endpoints
  - `GET /api/v1/projects/:id/team` — list team members
  - `POST /api/v1/projects/:id/team` — add team member
  - `DELETE /api/v1/projects/:id/team/:userId` — remove team member
  - *Dependencies:* Project endpoints, User endpoints

- [ ] **3.7** Task endpoints
  - `GET /api/v1/tasks` — list tasks (with filters)
  - `POST /api/v1/tasks` — create task
  - `GET /api/v1/tasks/:id` — get task detail
  - `PUT /api/v1/tasks/:id` — update task
  - `DELETE /api/v1/tasks/:id` — delete task
  - *Dependencies:* Project endpoints

- [ ] **3.8** Document endpoints
  - `GET /api/v1/documents` — list documents (with filters)
  - `POST /api/v1/documents` — upload document (multipart)
  - `GET /api/v1/documents/:id` — get document detail
  - `PUT /api/v1/documents/:id` — update document
  - `DELETE /api/v1/documents/:id` — delete document
  - `POST /api/v1/documents/:id/comments` — add comment
  - `GET /api/v1/documents/:id/comments` — list comments
  - *Dependencies:* Project endpoints, File upload service

- [ ] **3.9** Message endpoints
  - `GET /api/v1/projects/:id/messages` — list messages
  - `POST /api/v1/projects/:id/messages` — send message
  - *Dependencies:* Project endpoints

- [ ] **3.10** Announcement endpoints
  - `GET /api/v1/announcements` — list announcements
  - `POST /api/v1/announcements` — create announcement
  - `PUT /api/v1/announcements/:id` — update announcement
  - `DELETE /api/v1/announcements/:id` — delete announcement
  - *Dependencies:* None

- [ ] **3.11** Review endpoints
  - `GET /api/v1/reviews` — list reviews (with filters)
  - `POST /api/v1/reviews` — submit review
  - `GET /api/v1/reviews/:id` — get review detail
  - `PUT /api/v1/reviews/:id` — update review (approve/reject)
  - `GET /api/v1/reviews?status=APPROVED` — public approved reviews
  - *Dependencies:* Project endpoints, User endpoints

- [ ] **3.12** Service endpoints
  - `GET /api/v1/services` — list services
  - `POST /api/v1/services` — create service (admin)
  - `PUT /api/v1/services/:id` — update service (admin)
  - `DELETE /api/v1/services/:id` — deactivate service (admin)
  - *Dependencies:* None

- [ ] **3.13** Admin endpoints
  - `GET /api/v1/dashboard/admin` — admin dashboard data
  - `GET /api/v1/audit-logs` — audit log list
  - `GET /api/v1/settings` — system settings
  - `PUT /api/v1/settings` — update system settings
  - `GET /api/v1/roles` — list roles
  - `POST /api/v1/roles` — create role
  - `PUT /api/v1/roles/:id` — update role
  - `GET /api/v1/permissions` — list permissions
  - `POST /api/v1/permissions` — create permission
  - *Dependencies:* All entity classes

- [ ] **3.14** Dropdown endpoints
  - `GET /api/v1/dropdowns` — list all categories
  - `GET /api/v1/dropdowns/:category` — get category values
  - `POST /api/v1/dropdowns` — create category
  - `PUT /api/v1/dropdowns/:id` — update category
  - `DELETE /api/v1/dropdowns/:id` — delete category
  - `POST /api/v1/dropdowns/:category/values` — add value
  - `PUT /api/v1/dropdowns/values/:id` — update value
  - `DELETE /api/v1/dropdowns/values/:id` — delete value
  - *Dependencies:* None

- [ ] **3.15** Notification endpoints
  - `GET /api/v1/notifications` — list notifications for current user
  - `PUT /api/v1/notifications/:id/read` — mark as read
  - `PUT /api/v1/users/me/notifications` — update notification preferences
  - *Dependencies:* User endpoints

- [ ] **3.16** Landing page endpoint
  - `GET /api/v1/landing` — public landing page data (approved reviews, features)
  - *Dependencies:* Review endpoints

- [ ] **3.17** Swagger/OpenAPI integration
  - SpringDoc OpenAPI 2 dependency
  - `@Operation`, `@ApiResponses` annotations on all endpoints
  - Swagger UI at `/swagger-ui.html`
  - OpenAPI 3.0 spec at `/v3/api-docs`
  - *Dependencies:* All endpoints complete

- [ ] **3.18** Docker Compose integration
  - Backend service definition
  - Network: `portal-network`
  - Depends on: PostgreSQL
  - Health check: `/actuator/health`
  - *Dependencies:* Dockerfile, all endpoints

---

### Phase 4: Integration & Testing

- [ ] **4.1** Frontend-Backend integration
  - Replace all dummy data with API calls
  - Configure API base URL (`http://localhost:8080/api/v1`)
  - Implement HTTP interceptors (auth token, error handling)
  - *Dependencies:* All frontend components, all API endpoints

- [ ] **4.2** End-to-end testing
  - User registration and login flow
  - Project creation wizard
  - Task assignment and status updates
  - Document upload and comments
  - Message sending and notification
  - Review submission and approval
  - *Dependencies:* All frontend and backend complete

- [ ] **4.3** Docker Compose full stack
  - Services: frontend, landing, api, postgres
  - Volumes: `uploads`, `postgres-data`
  - Network: `portal-network`
  - Health checks and restart policies
  - *Dependencies:* All Dockerfiles, all services

- [ ] **4.4** User Acceptance Testing (UAT)
  - Client workflow: register, login, view dashboard, create project, assign tasks, upload documents, send messages, submit review
  - Provider workflow: login, view all projects, manage team, approve reviews, create announcements
  - Admin workflow: login, manage users, configure roles, view audit logs, update system settings
  - *Dependencies:* Full stack running

---

### Execution Order Summary

```
Phase 1 (Frontend) → Phase 2 (Database) → Phase 3 (API) → Phase 4 (Integration & UAT)
      |                       |                      |
  Dummy data              Schema +              All endpoints
  UI validation           Entity classes        Swagger + Docker
  Early feedback          Seed data             Integration
```

**Key principle:** Frontend first with dummy data allows rapid UI iteration. Changes to the UI may require adjustments to the API and database — those are tracked in the "API impact" notes above. Once the UI is approved, the API and database are built to match.
