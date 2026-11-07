<template>
  <div class="auth-wrap">
    <div class="auth-card card">
      <h2>登录 CodeJudge</h2>
      <input class="input" v-model="username" placeholder="用户名" />
      <input class="input" v-model="password" type="password" placeholder="密码" @keyup.enter="onLogin" />
      <div v-if="err" class="err">{{ err }}</div>
      <button class="btn" :disabled="loading" @click="onLogin">登录</button>
      <div class="tip">还没有账号？<router-link to="/register">注册</router-link></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { login } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';

const username = ref('');
const password = ref('');
const err = ref('');
const loading = ref(false);
const router = useRouter();
const auth = useAuthStore();

async function onLogin() {
  err.value = '';
  if (!username.value || !password.value) {
    err.value = '请输入用户名和密码';
    return;
  }
  loading.value = true;
  try {
    const vo = await login(username.value, password.value);
    await auth.loginAs(vo.token, vo.user);
    router.push('/problems');
  } catch (e) {
    err.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.auth-wrap { display: flex; justify-content: center; margin-top: 80px; }
.auth-card { width: 340px; display: flex; flex-direction: column; gap: 12px; }
.auth-card h2 { margin: 0 0 8px; }
.err { color: var(--danger); font-size: 13px; }
.tip { font-size: 13px; color: var(--muted); text-align: center; }
</style>
