# Task Manager — Frontend

A thin **React (Vite + TypeScript)** SPA: a typed client over the [Task Manager API](../README.md), not a
separate product. It logs in against Keycloak with **Authorization Code + PKCE** and calls the
JWT-protected API cross-origin.

- **Auth** — `react-oidc-context` / `oidc-client-ts` against the public `taskmanager-spa` Keycloak client
- **Data** — [TanStack Query](https://tanstack.com/query); a typed API client (`src/api`) injects the Bearer token
- **Features** — sign-in/out, task list, create, complete-toggle, delete, prefix full-text search with
  highlighting, and (for admins) an "All tasks" view over the whole tenant

## Run

```bash
cp .env.example .env.local     # API base + Keycloak authority/client id (defaults are fine locally)
npm install
npm run dev                    # http://localhost:5173
```

Needs the backing services (`docker compose up -d`) and the API (`./mvnw spring-boot:run`) up, so Keycloak
(`:8082`) and the API (`:8080`) are reachable. Sign in with a dev user (e.g. `user1` / `userpass`, or
`admin1` / `adminpass` for the admin view).

## Scripts

- `npm run dev` — dev server (HMR)
- `npm run build` — type-check + production build (`tsc -b && vite build`)
- `npm run lint` — [oxlint](https://oxc.rs)
- `npm run preview` — serve the production build

## Layout

```
src/
  api/      typed API client + task endpoints
  auth/     OIDC config + role helper
  tasks/    hooks (TanStack Query) + views (personal + admin)
  App.tsx   auth gate + layout
```
