<template>
  <div class="layout">
    <header class="topbar">
      <div class="brand">CodeJudge</div>
      <nav class="nav">
        <router-link to="/problems">题目</router-link>
        <router-link to="/submissions">我的提交</router-link>
        <router-link to="/leaderboard">排行榜</router-link>
        <router-link v-if="auth.user?.role === 1" to="/admin/problems">题目管理</router-link>
      </nav>
      <div class="user">
        <template v-if="auth.user">
          <span class="nick">{{ auth.user.nickname }}</span>
          <a class="logout" @click="onLogout">退出</a>
        </template>
        <router-link v-else to="/login">登录</router-link>
      </div>
    </header>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth';
import { useRouter } from 'vue-router';

const auth = useAuthStore();
const router = useRouter();

function onLogout() {
  auth.logout();
  router.push('/login');
}
</script>

<style scoped>
.layout { min-height: 100vh; }
.topbar {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 56px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 10;
}
.brand { font-weight: 700; font-size: 18px; color: var(--primary); }
.nav { display: flex; gap: 18px; flex: 1; }
.nav a { color: var(--text); font-weight: 500; }
.nav a.router-link-active { color: var(--primary); }
.user { display: flex; align-items: center; gap: 12px; }
.nick { font-weight: 500; }
.logout { cursor: pointer; color: var(--muted); }
.content { max-width: 1080px; margin: 20px auto; padding: 0 24px; }
</style>
