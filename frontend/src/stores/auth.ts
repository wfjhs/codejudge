import { defineStore } from 'pinia';
import { ref } from 'vue';
import { me } from '@/api/auth';
import type { UserVO } from '@/types';

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') || '');
  const user = ref<UserVO | null>(null);

  function setToken(t: string) {
    token.value = t;
    localStorage.setItem('token', t);
  }

  async function fetchMe() {
    if (!token.value) return;
    try {
      user.value = await me();
    } catch {
      // token 失效：清状态
      logout();
    }
  }

  async function loginAs(t: string, u: UserVO) {
    setToken(t);
    user.value = u;
  }

  function logout() {
    token.value = '';
    user.value = null;
    localStorage.removeItem('token');
  }

  return { token, user, setToken, fetchMe, loginAs, logout };
});
