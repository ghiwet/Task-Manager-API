import { useEffect, useState, type FormEvent } from 'react';
import type { Task } from '../types.ts';
import { useCreateTask, useDeleteTask, useSearchTasks, useTasks, useUpdateTask } from './hooks.ts';
import { TaskItem } from './TaskItem.tsx';

export function TasksView() {
  const tasks = useTasks();
  const createMut = useCreateTask();
  const updateMut = useUpdateTask();
  const deleteMut = useDeleteTask();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  useEffect(() => {
    const id = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(id);
  }, [query]);
  const search = useSearchTasks(debouncedQuery);

  const busy = createMut.isPending || updateMut.isPending || deleteMut.isPending;
  const searching = debouncedQuery.trim().length > 0;

  const onCreate = (e: FormEvent) => {
    e.preventDefault();
    const t = title.trim();
    if (!t) return;
    createMut.mutate(
      { title: t, description: description.trim() || undefined, completed: false },
      {
        onSuccess: () => {
          setTitle('');
          setDescription('');
        },
      },
    );
  };

  const onToggle = (task: Task) => {
    updateMut.mutate({
      id: task.id,
      task: {
        title: task.title,
        description: task.description ?? undefined,
        completed: !task.completed,
      },
    });
  };

  return (
    <section className="tasks">
      <form className="task-form" onSubmit={onCreate}>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="New task title"
          maxLength={255}
          required
        />
        <input
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Description (optional)"
        />
        <button type="submit" disabled={busy}>
          Add
        </button>
      </form>
      {createMut.isError && <p className="error">Create failed: {createMut.error.message}</p>}

      <input
        className="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search tasks…"
      />

      {searching ? (
        <div>
          {search.isLoading && <p className="muted">Searching…</p>}
          {search.error && <p className="error">Search failed: {search.error.message}</p>}
          <ul className="task-list">
            {search.data?.results.map((r) => (
              // highlights are HTML-escaped server-side; only the <em> match tags are live
              <li key={r.id} className="task-item">
                <span dangerouslySetInnerHTML={{ __html: r.highlights[0] ?? r.title }} />
              </li>
            ))}
          </ul>
          {search.data?.results.length === 0 && <p className="muted">No matches.</p>}
        </div>
      ) : (
        <div>
          {tasks.isLoading && <p className="muted">Loading tasks…</p>}
          {tasks.error && <p className="error">Could not load tasks: {tasks.error.message}</p>}
          <ul className="task-list">
            {tasks.data?.content.map((task) => (
              <TaskItem
                key={task.id}
                task={task}
                onToggle={onToggle}
                onDelete={(id) => deleteMut.mutate(id)}
                busy={busy}
              />
            ))}
          </ul>
          {tasks.data?.content.length === 0 && <p className="muted">No tasks yet — add one above.</p>}
        </div>
      )}
    </section>
  );
}
