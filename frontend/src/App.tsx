import { useEffect } from 'react';
import { useAuth } from 'react-oidc-context';
import { setTokenProvider } from './api/client.ts';
import { TasksView } from './tasks/TasksView.tsx';
import './App.css';

function App() {
  const auth = useAuth();

  // Keep the API client's Bearer token in sync with the current session (re-runs on token refresh).
  useEffect(() => {
    setTokenProvider(() => auth.user?.access_token);
  }, [auth.user]);

  if (auth.isLoading) {
    return (
      <main className="app">
        <p className="muted">Loading…</p>
      </main>
    );
  }

  if (auth.error) {
    return (
      <main className="app">
        <p className="error">Authentication error: {auth.error.message}</p>
      </main>
    );
  }

  if (!auth.isAuthenticated) {
    return (
      <main className="app">
        <header className="app-header">
          <h1>Task Manager</h1>
        </header>
        <p className="muted">Sign in with Keycloak to manage your tasks.</p>
        <button type="button" onClick={() => void auth.signinRedirect()}>
          Sign in
        </button>
      </main>
    );
  }

  const name =
    auth.user?.profile.preferred_username ?? auth.user?.profile.name ?? 'user';

  return (
    <main className="app">
      <header className="app-header">
        <h1>Task Manager</h1>
        <div className="user">
          <span className="muted">Signed in as {name}</span>
          <button type="button" onClick={() => void auth.signoutRedirect()}>
            Sign out
          </button>
        </div>
      </header>
      <TasksView />
    </main>
  );
}

export default App;
