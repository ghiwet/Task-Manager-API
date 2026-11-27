-- Threat #54 follow-up: V11's NOT VALID check still fires on any UPDATE of an older over-long row, so
-- such a task couldn't be edited at all. Trim those rows to the limit, then validate the constraint so
-- every row satisfies it. Derived copies (search index, embeddings) catch up on the task's next event.

UPDATE tasks SET description = left(description, 4000) WHERE char_length(description) > 4000;

ALTER TABLE tasks VALIDATE CONSTRAINT chk_tasks_description_length;
