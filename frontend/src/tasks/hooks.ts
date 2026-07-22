import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createTask, deleteTask, listTasks, searchTasks, updateTask } from '../api/tasks.ts';
import type { TaskCreate } from '../types.ts';

const TASKS_KEY = ['tasks'] as const;

export function useTasks() {
  return useQuery({ queryKey: TASKS_KEY, queryFn: () => listTasks(0, 50) });
}

export function useCreateTask() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (task: TaskCreate) => createTask(task),
    onSuccess: () => qc.invalidateQueries({ queryKey: TASKS_KEY }),
  });
}

export function useUpdateTask() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: number; task: TaskCreate }) => updateTask(vars.id, vars.task),
    onSuccess: () => qc.invalidateQueries({ queryKey: TASKS_KEY }),
  });
}

export function useDeleteTask() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteTask(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: TASKS_KEY }),
  });
}

export function useSearchTasks(q: string) {
  return useQuery({
    queryKey: ['tasks', 'search', q],
    queryFn: () => searchTasks(q),
    enabled: q.trim().length > 0,
  });
}
