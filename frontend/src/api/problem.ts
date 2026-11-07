import { get, post, put, del } from './request';
import type { PageResult, ProblemDetailVO, ProblemListVO, AdminProblemDetailVO, ProblemSaveDTO } from '@/types';

export function problemPage(params: {
  page?: number;
  size?: number;
  difficulty?: number | null;
  keyword?: string;
  sortBy?: string;
  asc?: boolean;
}) {
  return get<PageResult<ProblemListVO>>('/problems', { params });
}

export function problemDetail(id: number) {
  return get<ProblemDetailVO>(`/problems/${id}`);
}

export function adminProblemList() {
  return get<AdminProblemDetailVO[]>('/admin/problems');
}

export function adminProblemDetail(id: number) {
  return get<AdminProblemDetailVO>(`/admin/problems/${id}`);
}

export function createProblem(dto: ProblemSaveDTO) {
  return post<number>('/admin/problems', dto);
}

export function updateProblem(id: number, dto: ProblemSaveDTO) {
  return put<void>(`/admin/problems/${id}`, dto);
}

export function deleteProblem(id: number) {
  return del<void>(`/admin/problems/${id}`);
}
