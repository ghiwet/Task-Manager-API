import { useEffect, useState } from 'react';
import { useAllTasks, useDeleteTask, useSearchAllTasks } from './hooks.ts';

export function AdminTasks() {
  const all = useAllTasks();
  const deleteMut = useDeleteTask();

  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  useEffect(() => {
    const id = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(id);
  }, [query]);
  const search = useSearchAllTasks(debouncedQuery);
  const searching = debouncedQuery.trim().length > 0;

  return (
    <div>
      <h2 className="admin-heading">All tasks in your tenant</h2>
      <input
        className="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search all tasks…"
      />

      {searching ? (
        <div>
          {search.isLoading && <p className="muted">Searching…</p>}
          {search.error && <p className="error">Search failed: {search.error.message}</p>}
          <ul className="task-list">
            {search.data?.results.map((r) => (
              <li key={r.id} className="task-item">
                {/* highlights are HTML-escaped server-side; only the <em> match tags are live */}
                <span dangerouslySetInnerHTML={{ __html: r.highlights[0] ?? r.title }} />
                <span className="task-owner muted">owner: {r.owner}</span>
                <button
                  type="button"
                  className="link"
                  onClick={() => deleteMut.mutate(r.id)}
                  disabled={deleteMut.isPending}
                >
                  Delete
                </button>
              </li>
            ))}
          </ul>
          {search.data?.results.length === 0 && <p className="muted">No matches.</p>}
        </div>
      ) : (
        <div>
          {all.isLoading && <p className="muted">Loading…</p>}
          {all.error && <p className="error">Could not load tasks: {all.error.message}</p>}
          <ul className="task-list">
            {all.data?.content.map((task) => (
              <li key={task.id} className="task-item">
                <span className={task.completed ? 'task-title done' : 'task-title'}>
                  <span>{task.title}</span>
                </span>
                <span className="task-owner muted">owner: {task.owner}</span>
                <button
                  type="button"
                  className="link"
                  onClick={() => deleteMut.mutate(task.id)}
                  disabled={deleteMut.isPending}
                >
                  Delete
                </button>
              </li>
            ))}
          </ul>
          {all.data?.content.length === 0 && <p className="muted">No tasks in the tenant yet.</p>}
        </div>
      )}
    </div>
  );
}
