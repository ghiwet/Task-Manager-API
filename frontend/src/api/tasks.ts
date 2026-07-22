import { apiFetch } from './client';
import type { Page, Task, TaskCreate, TaskSearchResponse } from '../types';

export function listTasks(page = 0, size = 20): Promise<Page<Task>> {
  return apiFetch<Page<Task>>(`/api/v1/tasks?page=${page}&size=${size}`);
}

export function createTask(task: TaskCreate): Promise<Task> {
  return apiFetch<Task>('/api/v1/tasks', {
    method: 'POST',
    body: JSON.stringify(task),
  });
}

export function updateTask(id: number, task: TaskCreate): Promise<Task> {
  return apiFetch<Task>(`/api/v1/tasks/${id}`, {
    method: 'PUT',
    body: JSON.stringify(task),
  });
}

export function deleteTask(id: number): Promise<void> {
  return apiFetch<void>(`/api/v1/tasks/${id}`, { method: 'DELETE' });
}

export function searchTasks(q: string): Promise<TaskSearchResponse> {
  return apiFetch<TaskSearchResponse>(`/api/v1/tasks/search?q=${encodeURIComponent(q)}`);
}
