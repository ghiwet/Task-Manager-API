-- The app_rls role's password comes from config, not from V5's dev default ('app_rls_pass').
-- The app_rls_password placeholder is spring.datasource.password (see application.properties), so the
-- role and the app's connection always use the same secret. Flyway re-runs this repeatable migration
-- whenever the resolved value changes, which is how the password is rotated.
-- Flyway doesn't replace placeholders inside dollar-quoted bodies, so the value goes in through a
-- transaction-local setting (SET returns no rows, so nothing is echoed). It must not contain a single quote.

SET LOCAL taskmanager.app_rls_password = '${app_rls_password}';

DO $do$
DECLARE
    pw TEXT := current_setting('taskmanager.app_rls_password');
BEGIN
    IF pw = '' THEN
        RAISE EXCEPTION 'app_rls password is not configured (spring.datasource.password)';
    END IF;
    EXECUTE format('ALTER ROLE app_rls PASSWORD %L', pw);
END
$do$;
