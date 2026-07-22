import type { Task } from '../types.ts';

interface Props {
  task: Task;
  onToggle: (task: Task) => void;
  onDelete: (id: number) => void;
  busy: boolean;
}

export function TaskItem({ task, onToggle, onDelete, busy }: Props) {
  return (
    <li className="task-item">
      <label className={task.completed ? 'task-title done' : 'task-title'}>
        <input
          type="checkbox"
          checked={task.completed}
          onChange={() => onToggle(task)}
          disabled={busy}
        />
        <span>{task.title}</span>
      </label>
      {task.description && <span className="task-desc muted">{task.description}</span>}
      <button type="button" className="link" onClick={() => onDelete(task.id)} disabled={busy}>
        Delete
      </button>
    </li>
  );
}
