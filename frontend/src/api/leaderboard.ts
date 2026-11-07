import { get } from './request';
import type { LeaderboardVO } from '@/types';

export function leaderboard(top: number) {
  return get<LeaderboardVO[]>('/leaderboard', { params: { top } });
}
