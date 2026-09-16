# TaceIQ — Codebase Overview

> **Authoritative documentation is now in `README.md`.** This file is a concise structural reference.

## Structure

```
taceIQ/
├── frontend/                 # React 18 + TypeScript + Vite
│   ├── src/
│   │   ├── api/              # client.ts (Axios + JWT)
│   │   ├── components/       # ProtectedRoute, PlatformAdminRoute
│   │   ├── layouts/          # OrganisationLayout, AuthLayout
│   │   ├── pages/
│   │   │   ├── LoginPage.tsx
│   │   │   ├── organisation/
│   │   │   │   ├── Dashboard.tsx
│   │   │   │   ├── EvidenceGraphPage.tsx
│   │   │   │   ├── InvestigationsPage.tsx
│   │   │   │   ├── InvestigationWorkspacePage.tsx
│   │   │   │   └── investigation/  # Evidence, Timeline (Evidence Chronology), Checks, Notes, Decisions, FinalResult, AI, Reports
│   │   │   └── platform/
│   │   ├── routes/           # index.tsx
│   │   ├── services/         # investigationService.ts, fileService.ts
│   │   ├── store/            # authStore.ts (Zustand)
│   │   └── types/            # investigation.ts, auth.ts
│   ├── package.json
│   └── vite.config.ts
├── src/main/java/com/taceiq/
│   ├── controller/           # Auth, User, Organisation, Integration, File, Graph, Investigation, Complaint, Timeline, Checks, Notes, Decisions, FinalResult, Report, AI
│   ├── entity/               # User, Organisation, Role, Permission, Integration, IntegrationSync, CanonicalEvidence, File, Complaint, Investigation, InvestigationEvidence, InvestigationCheck, InvestigationNote, InvestigationDecision, InvestigationFinalResult
│   ├── dto/                  # Request/Response DTOs
│   ├── repository/           # Spring Data JPA (all org-scoped)
│   ├── security/             # AuthorizationService, JwtService, SecurityConfig, filters
│   ├── service/              # IntegrationSync, File, GraphProjection/Validator/Readiness, Investigation* services, AI
│   ├── graph/                # GraphController, GraphQueryRepository, dto
│   ├── ai/                   # Gemini provider, prompt builder
│   └── resources/
│       ├── application.properties   # env placeholders (no real secrets)
│       └── db/migration/            # V2025... canonical, investigation, checks, notes, decisions
├── docs/
│   ├── sample-source-response.json
│   ├── phase1-canonical-model.md
│   └── phase1-data-contract.md
├── .env.example
├── .gitignore
├── pom.xml
└── README.md                 # authoritative setup, workflow, security, limitations
```

## Notes

- **Timeline** is evidence-derived (`EVIDENCE_CREATED` from `sourceCreatedAt`, `EVIDENCE_UPDATED` when distinct) — not an audit log.
- **Graph** is derived (`PostgreSQL → Neo4j MERGE`), gated by `GraphReady`.
- See `README.md` for workflow, env vars, and intentional limitations (no SFTP, no OCR, no embeddings).
