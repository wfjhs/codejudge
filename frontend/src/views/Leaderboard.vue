<template>
  <div>
    <h2>解题排行榜</h2>
    <div class="card">
      <table class="tbl">
        <thead>
          <tr>
            <th style="width: 80px">排名</th>
            <th>用户</th>
            <th style="width: 120px">解题数</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.userId">
            <td>{{ r.rank }}</td>
            <td>{{ r.nickname }}</td>
            <td>{{ r.solved }}</td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="3" class="empty">还没有人解题，快来抢占榜首</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { leaderboard } from '@/api/leaderboard';
import type { LeaderboardVO } from '@/types';

const list = ref<LeaderboardVO[]>([]);

onMounted(async () => {
  list.value = await leaderboard(20);
});
</script>

<style scoped>
.tbl { width: 100%; border-collapse: collapse; }
.tbl th, .tbl td { text-align: left; padding: 10px 8px; border-bottom: 1px solid var(--border); }
.empty { text-align: center; color: var(--muted); padding: 24px; }
</style>
