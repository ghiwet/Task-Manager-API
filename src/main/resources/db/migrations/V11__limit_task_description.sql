-- Threat #54: cap description length (matches TaskCreateDto.DESCRIPTION_MAX).
-- NOT VALID: checks new/updated rows only, so DBs with older long rows still migrate.

ALTER TABLE tasks
    ADD CONSTRAINT chk_tasks_description_length CHECK (char_length(description) <= 4000) NOT VALID;
