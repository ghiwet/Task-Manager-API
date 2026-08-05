# ⚛️ Task Manager — Demo Script (Full-Stack)

A ~8–10 min live walkthrough for a **full-stack** audience: leads with the React SPA and the frontend
engineering, with the production-shaped backend/platform as the closer. Includes a 3-min lightning
version and a screenshot checklist. (For a backend/SRE/platform audience, see `demo-script.md`.)

## 0. Before you present (do this early — startup is slow)

```bash
# fresh DB so owners are usernames + no stale data
docker compose down -v && docker compose up -d      # Postgres, Keycloak, Kafka, Redis, ES, Prometheus, Grafana, Tempo
./mvnw spring-boot:run                               # wait for "Started TaskmanagerApplication"
cd frontend && npm run dev                           # http://localhost:5173
```

**Pre-seed data** (so the admin view isn't empty): sign in as **`user2` / `userpass`**, create a few
tasks (`task1`, `task2`, "Buy milk"), sign out. Leave `user1` empty to create live.

**Open:** SPA `:5173`, your editor on `frontend/src`, Swagger `:8080/swagger-ui`, Grafana `:3000`.

**One-liner to open with:** *"A real full-stack app, end to end — a typed React SPA over a JWT-secured
Spring Boot API: OIDC/PKCE login, server-state with TanStack Query, and a backend that's genuinely
production-shaped underneath."*

---

## 1. The product — a real SPA (~3 min)

| Do | Say |
|---|---|
| Open SPA → **Sign in** → Keycloak → `user1` / `userpass` | "Real OAuth2 — Authorization Code + PKCE against Keycloak. No password in my app, no client secret in the browser." |
| Create a task (appears instantly), toggle complete, delete | "Server state is TanStack Query — mutations invalidate the list, so the UI stays in sync without hand-rolled state." |
| Type in search, pausing between keystrokes | "Debounced — one request when you stop typing; prefix-matched and highlighted: `task` finds `task1`/`task2`." |
| Sign in as `admin1` / `adminpass` → **"All tasks (admin)"**; resize the window | "The admin view is role-gated in the client, and the layout is responsive — loading and error states throughout, not just the happy path." |

📸 login · task list + highlight · admin "All tasks" · mobile width

---

## 2. The frontend, built right (~2 min — show the code)

**Types — the client mirrors the backend DTOs** *(a shape change breaks the build, not production)*
```ts
// src/api/tasks.ts
export function listTasks(page = 0, size = 20): Promise<Page<Task>> {
  return apiFetch(`/api/v1/tasks?page=${page}&size=${size}`);
}
```

**Data — Query hooks own server state** *(caching, background refetch, invalidation on mutation)*
```ts
// src/tasks/hooks.ts
export function useCreateTask() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (t: TaskCreate) => createTask(t),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tasks'] }),
  });
}
```

**Auth** — `react-oidc-context` does the PKCE dance; the API client reads the current access token from
a provider and injects it as a Bearer header (refresh is automatic). The admin gate decodes
`realm_access.roles` from the token.

**Build** — Vite + TypeScript strict, oxlint, a clean `api / auth / tasks` module layout. A thin, honest
client — not a second backend.

📸 the typed API client and a Query hook side by side

---

## 3. The API behind it (~2–3 min)

- **Auth** — "The API is a JWT resource server — every request is owner-scoped by the token, and admin
  endpoints are locked down with method security."
- **Tenancy** — "Multi-tenancy is enforced by Postgres row-level security, not just app code — an admin
  only ever sees their own tenant." *(Show: `admin1`/tenant-b sees tenant-b; `user1`/tenant-a invisible.)*
- **Search** — "Elasticsearch, kept in sync event-driven off a Kafka outbox — never a dual-write — and
  highlights are HTML-escaped server-side."

📸 Swagger UI · the admin cross-user list

---

## 4. End to end — and shipped like a service (~1–2 min)

- **Trace** — "One click in the browser becomes a single trace: SPA → API → Postgres, and across the
  Kafka producer→consumer hop." *(Show: Grafana → Explore → Tempo → a `POST /api/v1/tasks` trace.)*
- **CORS** — "The SPA calls the API cross-origin — CORS is configured for its origin, Bearer tokens only,
  no cookies."
- **Delivery** — "And it ships like a real service — CI builds and tests both sides, Helm-packaged,
  delivered by Argo CD (GitOps), with SLOs and burn-rate alerts. Happy to go deep on any of it."

---

## ⚡ Lightning version (~3 min)

SPA login (PKCE) → create + search + admin view → open the typed API client & a Query hook → one Tempo
trace, SPA to Kafka.

## 📸 Screenshots to capture (`docs/images/`)

SPA login · task list + highlight · admin "All tasks" · mobile width · typed API client (code) · a Query
hook (code) · Swagger UI · a Tempo trace.

## 🧠 Q&A cheat sheet

- **"How do you manage server state?"** TanStack Query — caching + invalidation on mutation. No Redux;
  this app doesn't need global client state.
- **"How's the token handled?"** PKCE via `react-oidc-context`; the API client pulls the current access
  token from a provider set in an effect and sends it as a Bearer header. Silent renew keeps it fresh.
- **"Why not Next.js?"** The backend is Spring Boot — a Node SSR layer would be redundant. A Vite SPA is
  the honest fit; I'd reach for Next.js where SSR / server components actually earn their place.
- **"Type safety across the stack?"** TS interfaces mirror the Java DTOs and the API client is fully
  typed, so a contract change surfaces at compile time.
