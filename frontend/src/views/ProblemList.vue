<template>
  <div>
    <div class="head">
      <h2>题目列表</h2>
      <div class="filters">
        <select v-model="difficulty" @change="reload">
          <option :value="null">全部难度</option>
          <option :value="1">简单</option>
          <option :value="2">中等</option>
          <option :value="3">困难</option>
        </select>
        <input class="input" v-model="keyword" placeholder="搜索题目标题" @keyup.enter="reload" />
        <button class="btn ghost" @click="reload">搜索</button>
      </div>
    </div>

    <div class="card">
      <table class="tbl">
        <thead>
          <tr>
            <th style="width: 56px">#</th>
            <th>标题</th>
            <th style="width: 90px">难度</th>
            <th style="width: 110px">通过率</th>
            <th style="width: 80px">提交</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in list" :key="p.id" @click="go(p.id)">
            <td>{{ p.id }}</td>
            <td class="title">{{ p.title }}</td>
            <td>
              <span class="tag" :class="diffClass(p.difficulty)">{{ diffText(p.difficulty) }}</span>
            </td>
            <td>{{ p.acceptRate }}%</td>
            <td>{{ p.submitCount }}</td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="5" class="empty">暂无题目</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pager" v-if="totalPages > 1">
      <button class="btn ghost" :disabled="page <= 1" @click="prev">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button class="btn ghost" :disabled="page >= totalPages" @click="next">下一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { problemPage } from '@/api/problem';
import type { ProblemListVO } from '@/types';

const router = useRouter();
const list = ref<ProblemListVO[]>([]);
const page = ref(1);
const totalPages = ref(1);
const difficulty = ref<number | null>(null);
const keyword = ref('');

function diffText(d: number) {
  return d === 1 ? '简单' : d === 2 ? '中等' : '困难';
}
function diffClass(d: number) {
  return d === 1 ? 'easy' : d === 2 ? 'medium' : 'hard';
}

async function reload() {
  page.value = 1;
  await load();
}
async function load() {
  const data = await problemPage({
    page: page.value,
    size: 10,
    difficulty: difficulty.value,
    keyword: keyword.value || undefined,
    sortBy: 'id',
    asc: true,
  });
  list.value = data.records;
  totalPages.value = data.pages;
}
function prev() {
  if (page.value > 1) {
    page.value--;
    load();
  }
}
function next() {
  if (page.value < totalPages.value) {
    page.value++;
    load();
  }
}
function go(id: number) {
  router.push(`/problems/${id}`);
}

onMounted(load);
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.filters { display: flex; gap: 8px; }
.filters .input { width: 200px; }
.tbl { width: 100%; border-collapse: collapse; }
.tbl th, .tbl td { text-align: left; padding: 10px 8px; border-bottom: 1px solid var(--border); }
.tbl tbody tr { cursor: pointer; }
.tbl tbody tr:hover { background: #fafbfc; }
.title { font-weight: 500; }
.empty { text-align: center; color: var(--muted); padding: 24px; }
.pager { display: flex; gap: 12px; align-items: center; justify-content: center; margin-top: 16px; }
</style>
