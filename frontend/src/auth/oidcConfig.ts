import { WebStorageStateStore } from 'oidc-client-ts';
import type { AuthProviderProps } from 'react-oidc-context';

// Authorization Code + PKCE (S256, oidc-client-ts default) against the public taskmanager-spa client.
// The code returns to the app root; onSigninCallback strips it from the URL after exchange.
export const oidcConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_KEYCLOAK_AUTHORITY ?? 'http://localhost:8082/realms/myrealm',
  client_id: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'taskmanager-spa',
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope: 'openid profile',
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};
