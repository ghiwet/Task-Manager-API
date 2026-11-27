-- Threat #13: usernames are globally unique, but the app's lookups run under RLS and only see the
-- caller's tenant. This owner-run (SECURITY DEFINER) check sees every tenant and returns only a
-- boolean, so registration answers the same whichever tenant holds the name.

CREATE FUNCTION username_taken(candidate VARCHAR) RETURNS BOOLEAN
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (SELECT 1 FROM users WHERE username = candidate)
$$;

REVOKE ALL ON FUNCTION username_taken(VARCHAR) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION username_taken(VARCHAR) TO app_rls;
