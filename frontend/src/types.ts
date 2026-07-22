// Mirrors the backend DTOs (com.example.taskmanager.dto).

export interface Task {
  id: number;
  version: number;
  title: string;
  description: string | null;
  completed: boolean;
  owner: string;
  createAt: string; // backend field name is `createAt`
  updatedAt: string;
}

export interface TaskCreate {
  title: string;
  description?: string;
  completed: boolean;
}

// Spring Data Page shape (the fields the UI uses).
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface TaskSearchResult {
  id: number;
  title: string;
  description: string | null;
  completed: boolean;
  highlights: string[];
}

export interface TaskSearchResponse {
  results: TaskSearchResult[];
  total: number;
}
