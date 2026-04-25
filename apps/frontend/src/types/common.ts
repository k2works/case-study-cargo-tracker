export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export type StatusType = 'success' | 'error' | 'warning' | 'info'
