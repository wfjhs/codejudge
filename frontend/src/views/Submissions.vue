<template>
  <div>
    <h2>我的提交</h2>
    <div class="card">
      <table class="tbl">
        <thead>
          <tr>
            <th style="width: 70px">#</th>
            <th>题目</th>
            <th style="width: 80px">语言</th>
            <th style="width: 100px">状态</th>
            <th style="width: 80px">得分</th>
            <th style="width: 100px">耗时</th>
            <th style="width: 150px">提交时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in list" :key="s.id" @click="go(s.id)">
            <td>{{ s.id }}</td>
            <td>{{ s.problemTitle || ('题目#' + s.problemId) }}</td>
            <td>{{ s.language.toUpperCase() }}</td>
            <td><span class="status" :class="s.status">{{ s.status }}</span></td>
            <td>{{ s.score }}</td>
            <td>{{ s.timeCost ?? '-' }}</td>
            <td class="time">{{ s.submitTime }}</td>
          </tr>
          <tr v-if="!list.length && !loading">
            <td colspan="7" class="empty">还没有提交记录</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pager" v-if="hasMore">
      <button class="btn ghost" :disabled="loading" @click="loadMore">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
    </div>
    <p class="end" v-else-if="list.length">— 已加载全部 —</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { mySubmissions } from '@/api/submission';
import type { SubmissionVO } from '@/types';

const router = useRouter();
const list = ref<SubmissionVO[]>([]);
const hasMore = ref(true);
const loading = ref(false);
const PAGE_SIZE = 15;

/**
 * 游标分页：每次带上一页最后一条的 (submitTime, id) 作为锚点。
 * 用游标而不是 page 参数，翻到很深的页码时查询耗时也不会变慢。
 */
async function load(reset = false) {
  if (loading.value || (!reset && !hasMore.value)) return;
  loading.value = true;
  try {
    const last = list.value[list.value.length - 1];
    const useCursor = !reset && !!last;
    const data = await mySubmissions({
      size: PAGE_SIZE,
      lastTime: useCursor ? last!.submitTime : undefined,
      lastId: useCursor ? last!.id : undefined,
    });
    list.value = reset ? data.records : [...list.value, ...data.records];
    hasMore.value = data.hasMore ?? data.records.length === PAGE_SIZE;
  } finally {
    loading.value = false;
  }
}

function loadMore() {
  load(false);
}
function go(id: number) {
  router.push(`/submissions/${id}`);
}

onMounted(() => load(true));
</script>

<style scoped>
.tbl { width: 100%; border-collapse: collapse; }
.tbl th, .tbl td { text-align: left; padding: 10px 8px; border-bottom: 1px solid var(--border); }
.tbl tbody tr { cursor: pointer; }
.tbl tbody tr:hover { background: #fafbfc; }
.time { color: var(--muted); font-size: 12px; }
.empty { text-align: center; color: var(--muted); padding: 24px; }
.pager { display: flex; gap: 12px; align-items: center; justify-content: center; margin-top: 16px; }
.end { text-align: center; color: var(--muted); font-size: 12px; margin-top: 16px; }
</style>
