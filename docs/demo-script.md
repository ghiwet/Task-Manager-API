# 🎬 Task Manager — Demo Script

A ~8–10 min live screen-share walkthrough built around the strongest signals (backend + platform),
with the React SPA as the visual hook. Includes a 3-min lightning version and a screenshot checklist.

## 0. Before you present (do this early — startup is slow)

```bash
# fresh DB so owners are usernames + no stale data
docker compose down -v && docker compose up -d      # Postgres, Keycloak, Kafka, Redis, ES, Prometheus, Grafana, Tempo
./mvnw spring-boot:run                               # wait for "Started TaskmanagerApplication"
cd frontend && npm run dev                           # http://localhost:5173
```

**Pre-seed data** (so the admin view isn't empty): log in as **`user2` / `userpass`**, create 2–3 tasks
(`task1`, `task2`, "Buy milk"), sign out. Leave `user1` empty to create live.

**Open these tabs:** SPA `:5173` · Swagger `:8080/swagger-ui` · Grafana `:3000` (admin/admin) ·
Prometheus `:9090` · Alertmanager `:9093`.

**One-liner to open with:** *"It's a task manager — but the point isn't the CRUD, it's that it's built
like a production service: multi-tenant with database-enforced isolation, event-driven with no lost
events, fully observed with SLOs, and shipped via GitOps."*

---

## 1. It works — the SPA (~2 min)

| Do | Say / Highlight | 📸 |
|---|---|---|
| Open SPA → **Sign in** → Keycloak → `user1` / `userpass` | "Login is real OAuth2 — Authorization Code **+ PKCE** against Keycloak, no client secret in the browser." | login page |
| Create a task, toggle complete, delete | "Standard CRUD, but the API is JWT-secured and every write also emits a Kafka event." | task list |
| Type `task` in search | "Search is Elasticsearch — **prefix-matched** and highlighted; `task` finds `task1`/`task2`." | search + highlight |
| Sign out → sign in as `admin1` / `adminpass` → **"All tasks (admin)"** | "Admins see **every task in their tenant** with the owner — and can search/delete across users. Owner is the username." | admin view |

---

## 2. Under the hood — the differentiators (~3–4 min; pick 2–3)

**A. Multi-tenancy via PostgreSQL Row-Level Security** *(strongest backend signal)*
- Say: *"Tenant isolation isn't just app code — it's enforced by Postgres RLS. The app connects as a
  non-owner role subject to RLS policies, so even a query that forgets to filter can't leak another
  tenant's data."*
- Show (Swagger or curl): `admin1` (tenant-b) hits `/tasks/all` → sees only tenant-b; `user1` is
  tenant-a and invisible to them. Mention `TenantIsolationTest` proves it as the `app_rls` role.

**B. Event-driven with a transactional outbox** *(no-lost-events story)*
- Say: *"Every task change writes to the DB and an outbox table in the **same transaction**, so they
  commit atomically. A relay ships them to Kafka; idempotent consumers keep the search index and
  embeddings in sync — no dual-write, no lost events."*
- Show: the Architecture diagram in the README (outbox flow), or Kafka UI (`:8085`) → `task-events`.

**C. Observability — one request, one trace** *(SRE signal)*
- Grafana → **Explore → Tempo** → find a recent trace of `POST /api/v1/tasks`.
- Say: *"One trace spans HTTP → JDBC → the Kafka producer→consumer boundary — a single end-to-end
  trace across the async hop."*
- Then Grafana → the **SLO dashboard** ("Task Manager — SLOs & Error Budgets"): availability/latency
  SLIs, error-budget gauges, burn-rate panels. 📸 both.

---

## 3. How it ships — platform (~2 min; screenshots if kind isn't up)

*(These need the kind cluster — if it's not running, use screenshots grabbed once.)*

- **CI/CD** — "Release is gated: a `v*` tag re-runs the full test suite via a reusable workflow before
  it publishes the image. Plus layered security scanning — CodeQL, Semgrep, Dependency-Check,
  TruffleHog, ZAP." 📸 the Actions run.
- **GitOps (Argo CD)** — show the app **Synced/Healthy**; `kubectl delete deploy task-manager-task-manager`
  → it **self-heals** in seconds. *"Terraform bootstraps the platform; Argo CD delivers the app from
  Git — pull-based, self-healing."* 📸 Argo CD tree.
- **Sealed Secrets** — `argocd/secrets/*.yaml` in Git is **encrypted**; the controller unseals it into
  the app's Secret. *"No plaintext secrets in Git."*
- **SLO alerting** — `promtool test rules` proves the burn-rate alerts fire; Alertmanager routes page
  vs ticket. 📸 Prometheus `/alerts`.

---

## 4. Close (~30s)

*"So: a small app, but production-shaped — RLS multi-tenancy, an outbox event pipeline, end-to-end
tracing with SLOs and burn-rate alerts, GitOps delivery with Sealed Secrets, and a thin React client
on top. Happy to go deep on any piece."*

---

## ⚡ Lightning version (~3 min)

SPA login (PKCE) → admin view → **one Tempo trace across Kafka** → the **SLO dashboard** → one line
each on RLS, outbox, GitOps. Done.

## 📸 Screenshots to capture for the README (`docs/images/`)

SPA login · task list + search highlight · admin "All tasks" · Swagger UI · Grafana SLO dashboard ·
a Tempo trace · Argo CD Synced/Healthy · a firing burn-rate alert.

## 🧠 Q&A cheat sheet

- **"Why RLS on top of app checks?"** Defense in depth — a bug or forgotten filter still can't cross
  tenants; the DB is the last line.
- **"How do you avoid lost events?"** Transactional outbox — the event commits with the DB change; a
  crash between relay-send and mark-published just redelivers, and consumers are idempotent.
- **"Owner is the username — isn't `sub` safer?"** Yes, `sub` is immutable; I chose username for
  readability and noted the rename tradeoff. Easy to switch back.
- **"How is this tested?"** ~60 integration tests on real Postgres/Kafka/ES via Testcontainers; RLS
  tests actually run as the `app_rls` role; alert rules unit-tested with `promtool`.
