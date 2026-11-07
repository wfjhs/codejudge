import { post, get } from './request';
import type { SubmissionVO, SubmissionDetailVO, PageResult } from '@/types';

export function submit(problemId: number, language: string, code: string, requestId: string) {
  return post<number>('/submissions', { problemId, language, code, requestId });
}

export function mySubmissions(params: {
  page?: number;
  size?: number;
  problemId?: number;
  /** 游标：上一页最后一条的提交时间（与 lastId 一起构成复合游标） */
  lastTime?: string;
  /** 游标：上一页最后一条的 id */
  lastId?: number;
}) {
  // 后端映射是 GET /api/submissions（列表天然就是「当前用户自己的提交」），
  // 不是 /submissions/my —— 写成 /my 会被 /{id} 捕获，"my" 转 Long 失败直接 500。
  //
  // 传了 lastTime + lastId 走游标分页（深分页不退化），
  // 只传 page + size 则走 offset 分页。
  return get<PageResult<SubmissionVO>>('/submissions', { params });
}

export function submissionDetail(id: number) {
  return get<SubmissionDetailVO>(`/submissions/${id}`);
}
