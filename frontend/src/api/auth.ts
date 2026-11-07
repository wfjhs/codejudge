import { post, get } from './request';
import type { LoginVO, UserVO } from '@/types';

export function login(username: string, password: string) {
  return post<LoginVO>('/auth/login', { username, password });
}

export function register(username: string, password: string, nickname: string) {
  return post<LoginVO>('/auth/register', { username, password, nickname });
}

export function me() {
  return get<UserVO>('/users/me');
}
