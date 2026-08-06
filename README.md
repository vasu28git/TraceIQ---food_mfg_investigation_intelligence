# AI-Powered Investigation Intelligence Platform for Food Manufacturing

An AI-assisted decision-support platform that helps food safety investigators trace incidents back to their most probable root cause — by automatically collecting evidence across disconnected enterprise systems, correlating it into an evidence graph, and generating transparent, evidence-grounded explanations.

> This is not a replacement for ERP, MES, LIMS, or existing traceability software. It sits above them, consuming their data to power faster, more consistent investigations.

---

## The Problem

Food manufacturing generates enormous volumes of operational data — supplier records, production batches, machine maintenance logs, QA/lab results, warehouse conditions, shipments, customer complaints, SOPs, and audit reports — but this data is scattered across siloed systems (ERP, MES, LIMS, WMS, CRM).

When an incident occurs, investigators must manually answer questions like:

- Which batch produced the affected product?
- Which supplier supplied the raw materials?
- Which machine processed the batch, and was it properly maintained?
- Were warehouse conditions acceptable?
- Has this problem happened before?
- Are other batches also affected?

Today this means manually cross-referencing spreadsheets and reports across departments — slow, inconsistent, and dependent entirely on investigator experience. Existing traceability platforms (SAP GBT, IBM Food Trust, TraceGains, FoodLogiQ, etc.) tell you *where a product went*, not *where an issue most likely originated* or *why*.

## The Solution

This platform automates the investigation process itself:

1. **Incident registration** — an investigation case is created from a complaint, recall, QA failure, or alert.
2. **Automated evidence collection** — related records are pulled automatically from all connected source systems.
3. **Evidence graph construction** — entities (batches, machines, suppliers, operators, warehouses, complaints) and their relationships are modeled as an interactive graph.
4. **Traceability** — backward (complaint → batch → machine → supplier) and forward (supplier → all affected batches) traversal.
5. **Correlation & scoring** — the platform identifies which entity is most consistently linked to the evidence (e.g. one machine common to all affected batches, overdue maintenance, recurring QA failures).
6. **Transparent reasoning** — every step of the investigation and every recommendation is shown, with the supporting evidence attached — never a black-box conclusion.
7. **AI investigation assistant** — a RAG-grounded LLM layer explains findings in natural language, cites evidence, compares against past investigations, and answers investigator questions.

The final decision always remains with the human investigator — the platform recommends and explains, it doesn't decide.

---

## Architecture

```
Source Systems (ERP, MES, LIMS, WMS, CRM, SOPs/Audit docs)
            │
            ▼
   Data Ingestion Layer  (FastAPI ETL services)
            │
            ▼
  Evidence Storage Layer
   ├── Neo4j (Graph DB)        → entities & relationships
   ├── PostgreSQL              → structured record details
   └── Vector Store (pgvector) → embedded SOPs / audits / past investigations
            │
            ▼
Investigation Intelligence Layer
   ├── Traceability Engine      → backward / forward graph traversal
   ├── Correlation & Scoring    → deterministic pattern detection (no AI)
   └── LLM Reasoning Layer      → RAG-grounded explanation (Gemini API)
            │
            ▼
   Presentation Layer
   ├── Investigator Dashboard   → interactive evidence graph, timeline
   └── AI Assistant Chat        → natural-language Q&A, report generation
```

**Design principle:** the correlation/scoring engine does all the actual reasoning using deterministic graph queries and scoring logic. The LLM layer never investigates on its own — it only explains evidence the engine has already gathered and ranked. This keeps recommendations auditable and trustworthy rather than AI-guessed.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend / orchestration | Java, Spring Boot, REST APIs, JWT auth |
| AI/ML microservice | Python, FastAPI |
| Graph database | Neo4j |
| Relational database | PostgreSQL (Supabase) |
| Vector store (RAG) | pgvector |
| LLM | Gemini API |
| Scoring model (optional) | XGBoost |
| Frontend | React, Cytoscape.js / react-force-graph for graph visualization |

---

## Source Data

The platform is demonstrated using a simulated dataset modeled on real food manufacturing operations, covering:

| System | Data |
|---|---|
| MES | Production batches, machines, operators |
| CMMS | Machine maintenance history |
| LIMS | QA/lab inspection reports |
| CRM | Customer complaints |
| ERP | Suppliers |
| WMS | Warehouse temperature/humidity logs |
| Distribution | Shipment records |
| Docs | SOPs, audit reports, past investigation summaries |

Recall categories and failure modes used in the simulated data are grounded in real public sources (openFDA Food Enforcement API, USDA FSIS recall data) to keep the dataset realistic. See [`/docs/data-dictionary.csv`](./docs/data-dictionary.csv) for the full field-level schema across all source files.

---

## Project Status

🚧 Work in progress — academic project.

- [x] Problem definition & architecture design
- [x] Simulated dataset (MES, LIMS, CRM, ERP, CMMS, WMS, Distribution, Docs)
- [ ] Evidence graph construction (Neo4j)
- [ ] Traceability engine (backward/forward)
- [ ] Correlation & scoring engine
- [ ] RAG pipeline + LLM reasoning layer
- [ ] Investigator dashboard (React)
- [ ] AI assistant chat interface

---

## Vision

Food manufacturing is the first implementation. The underlying Investigation Intelligence Engine is designed to be domain-independent — entity types and relationships are configuration, not hardcoded structure — so the same correlation and reasoning engine could later support investigations in pharmaceutical quality deviations, equipment failure analysis, automotive defects, or broader supply-chain incidents.

---

## Getting Started

```bash
# Clone the repo
git clone https://github.com/<your-username>/food-safety-investigation-ai.git
cd food-safety-investigation-ai

# Backend setup instructions to be added
# Frontend setup instructions to be added
```

*(Setup instructions will be filled in as each component is implemented.)*

---

## Author

Vasu — B.Tech Information Technology, Saveetha Engineering College

## License

MIT
