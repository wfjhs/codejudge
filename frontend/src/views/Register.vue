<template>
  <div class="auth-wrap">
    <div class="auth-card card">
      <h2>注册 CodeJudge</h2>
      <input class="input" v-model="username" placeholder="用户名（4-20 位）" />
      <input class="input" v-model="nickname" placeholder="昵称" />
      <input class="input" v-model="password" type="password" placeholder="密码（6 位以上）" />
      <input class="input" v-model="confirm" type="password" placeholder="确认密码" @keyup.enter="onRegister" />
      <div v-if="err" class="err">{{ err }}</div>
      <button class="btn" :disabled="loading" @click="onRegister">注册</button>
      <div class="tip">已有账号？<router-link to="/login">登录</router-link></div>
      <div class="hint">提示：第一个注册的用户自动成为管理员。</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { register } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';

const username = ref('');
const nickname = ref('');
const password = ref('');
const confirm = ref('');
const err = ref('');
const loading = ref(false);
const router = useRouter();
const auth = useAuthStore();

async function onRegister() {
  err.value = '';
  if (username.value.length < 4 || username.value.length > 20) {
    err.value = '用户名长度需在 4-20 之间';
    return;
  }
  if (password.value.length < 6) {
    err.value = '密码至少 6 位';
    return;
  }
  if (password.value !== confirm.value) {
    err.value = '两次密码不一致';
    return;
  }
  loading.value = true;
  try {
    const vo = await register(username.value, password.value, nickname.value || username.value);
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
.auth-wrap { display: flex; justify-content: center; margin-top: 60px; }
.auth-card { width: 340px; display: flex; flex-direction: column; gap: 12px; }
.auth-card h2 { margin: 0 0 8px; }
.err { color: var(--danger); font-size: 13px; }
.tip, .hint { font-size: 13px; color: var(--muted); text-align: center; }
</style>
