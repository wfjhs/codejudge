// 与后端 DTO / VO 一一对应的前端类型定义

export interface Result<T> {
  code: number;
  message: string;
  data: T;
  timestamp: number;
}

export interface LoginVO {
  token: string;
  user: UserVO;
}

export interface UserVO {
  id: number;
  username: string;
  nickname: string;
  role: number;
}

export interface ProblemListVO {
  id: number;
  title: string;
  difficulty: number;
  submitCount: number;
  acceptCount: number;
  acceptRate: number;
  createTime: string;
}

export interface SampleCaseVO {
  input: string;
  output: string;
}

export interface ProblemDetailVO {
  id: number;
  title: string;
  content: string;
  difficulty: number;
  timeLimit: number;
  memoryLimit: number;
  submitCount: number;
  acceptCount: number;
  acceptRate: number;
  createTime: string;
  samples: SampleCaseVO[];
}

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
  pages: number;
  /** 游标分页专用：是否还有下一页。offset 分页不返回此字段，用 pages 判断 */
  hasMore?: boolean;
}

export interface SubmissionVO {
  id: number;
  problemId: number;
  problemTitle?: string;
  language: string;
  status: string;
  score: number;
  timeCost: number;
  memoryCost: number;
  failCase: number | null;
  submitTime: string;
}

export interface SubmissionDetailVO extends SubmissionVO {
  code: string;
  errorMsg: string | null;
  judgeTime: string | null;
  totalCase: number;
}

export interface LeaderboardVO {
  rank: number;
  userId: number;
  nickname: string;
  solved: number;
}

export interface AdminProblemDetailVO extends ProblemDetailVO {
  cases: TestCaseVO[];
}

export interface TestCaseVO {
  id: number;
  input: string;
  output: string;
  isSample: number;
  score: number;
  sortOrder: number;
}

export interface TestCaseDTO {
  input: string;
  output: string;
  isSample: number;
  score: number;
}

export interface ProblemSaveDTO {
  title: string;
  content: string;
  difficulty: number;
  timeLimit: number;
  memoryLimit: number;
  cases: TestCaseDTO[];
}
