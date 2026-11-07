<template>
  <div v-if="detail" class="two-col">
    <div class="left card">
      <div class="phead">
        <h2>{{ detail.title }}</h2>
        <span class="tag" :class="diffClass(detail.difficulty)">{{ diffText(detail.difficulty) }}</span>
      </div>
      <div class="meta">
        时间限制 {{ detail.timeLimit }}ms · 内存限制 {{ detail.memoryLimit }}MB ·
        通过率 {{ detail.acceptRate }}% · 提交 {{ detail.submitCount }}
      </div>
      <div class="content" v-html="renderedContent"></div>

      <div class="samples">
        <div v-for="(s, i) in detail.samples" :key="i" class="sample">
          <div class="sample-title">示例 {{ i + 1 }}</div>
          <pre>输入：{{ s.input }}</pre>
          <pre>输出：{{ s.output }}</pre>
        </div>
      </div>
    </div>

    <div class="right card">
      <h3>提交代码</h3>
      <div class="row">
        <select v-model="language">
          <option value="java">Java</option>
          <option value="python">Python</option>
        </select>
        <button class="btn" :disabled="submitting" @click="onSubmit">提交</button>
      </div>
      <CodeEditor v-model="code" :placeholder="placeholder" />
      <div v-if="submitMsg" class="submit-msg">{{ submitMsg }}</div>

      <div v-if="latest" class="latest">
        <div class="latest-head">最近判题结果</div>
        <div>
          状态：<span class="status" :class="latest.status">{{ latest.status }}</span>
          <span v-if="latest.timeCost"> · 耗时 {{ latest.timeCost }}ms</span>
          <span v-if="latest.failCase"> · 第 {{ latest.failCase }} 个用例失败</span>
        </div>
        <pre v-if="latest.errorMsg" class="err-pre">{{ latest.errorMsg }}</pre>
      </div>
    </div>
  </div>
  <div v-else class="card">加载中…</div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute } from 'vue-router';
import { problemDetail } from '@/api/problem';
import { submit, submissionDetail } from '@/api/submission';
import { connectJudgeWs, type JudgePushPayload } from '@/api/websocket';
import type { ProblemDetailVO, SubmissionDetailVO } from '@/types';
import CodeEditor from '@/components/CodeEditor.vue';

const route = useRoute();
const id = Number(route.params.id);
const detail = ref<ProblemDetailVO | null>(null);
const language = ref('java');
const code = ref('');
const submitting = ref(false);
const submitMsg = ref('');
const latest = ref<SubmissionDetailVO | null>(null);

const placeholder = `public class Main {
    public static void main(String[] args) {
        // 读取输入，输出答案
    }
}`;

const renderedContent = computed(() => {
  // 题面是 Markdown，这里只做最轻量的换行/标题渲染，够用即可
  if (!detail.value) return '';
  return detail.value.content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/^# (.*)$/gm, '<h4>$1</h4>')
    .replace(/^## (.*)$/gm, '<h5>$1</h5>')
    .replace(/\n/g, '<br/>');
});

function diffText(d: number) {
  return d === 1 ? '简单' : d === 2 ? '中等' : '困难';
}
function diffClass(d: number) {
  return d === 1 ? 'easy' : d === 2 ? 'medium' : 'hard';
}

async function onSubmit() {
  if (!code.value.trim()) {
    submitMsg.value = '请先写代码再提交';
    return;
  }
  submitting.value = true;
  submitMsg.value = '';
  // 前端生成幂等键，重试时复用同一个值，避免重复提交
  const requestId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  let submissionId: number;
  try {
    submissionId = await submit(id, language.value, code.value, requestId);
  } catch (e) {
    submitMsg.value = (e as Error).message;
    submitting.value = false;
    return;
  }
  submitMsg.value = `提交成功，提交号 #${submissionId}，判题中…`;
  submitting.value = false;

  // 结果获取策略：优先 WebSocket 实时推送，连不上自动回退 HTTP 轮询。
  let settled = false;
  const settle = (vo: SubmissionDetailVO) => {
    if (settled) return;
    settled = true;
    latest.value = vo;
    submitMsg.value = `判题完成：提交号 #${submissionId}`;
    wsConn.close();
  };

  const token = localStorage.getItem('token') || '';
  const wsConn = connectJudgeWs(submissionId, token, {
    onResult: (p: JudgePushPayload) => settle(applyPayload(latest.value, p)),
    onError: () => pollResult(submissionId, settle),
  });

  // 竞态兜底：结果可能在 WS 连上之前就已写回，先拉一次，避免错过推送
  try {
    const d = await submissionDetail(submissionId);
    if (d.status !== 'PENDING' && d.status !== 'JUDGING') {
      settle(d);
    } else {
      latest.value = d;
    }
  } catch {
    // 忽略瞬时错误，交给 WS / 轮询
  }
}

/** 把 WS 推下来的结果字段合并进现有详情（WS 只带结果字段，其余沿用已有的详情） */
function applyPayload(base: SubmissionDetailVO | null, p: JudgePushPayload): SubmissionDetailVO {
  return {
    ...(base as object),
    id: base?.id ?? p.submissionId,
    status: p.status,
    score: p.score,
    timeCost: p.timeCost ?? 0,
    memoryCost: p.memoryCost ?? 0,
    errorMsg: p.errorMsg ?? null,
    failCase: p.failCase ?? null,
  } as SubmissionDetailVO;
}

async function pollResult(submissionId: number, onDone: (d: SubmissionDetailVO) => void) {
  for (let i = 0; i < 20; i++) {
    await new Promise((r) => setTimeout(r, 2000));
    try {
      const d = await submissionDetail(submissionId);
      latest.value = d;
      if (d.status !== 'PENDING' && d.status !== 'JUDGING') {
        onDone(d);
        return;
      }
    } catch {
      // 忽略轮询中的瞬时错误
    }
  }
  submitMsg.value = '判题超时，请稍后在"我的提交"查看';
}

onMounted(async () => {
  detail.value = await problemDetail(id);
});
</script>

<style scoped>
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; align-items: start; }
.phead { display: flex; align-items: center; gap: 12px; }
.phead h2 { margin: 0; }
.meta { color: var(--muted); font-size: 13px; margin: 8px 0 12px; }
.content { line-height: 1.7; }
.samples { margin-top: 16px; display: flex; flex-direction: column; gap: 10px; }
.sample { background: #f8f9fb; border-radius: 6px; padding: 10px; }
.sample-title { font-weight: 600; margin-bottom: 6px; }
.sample pre { margin: 4px 0; white-space: pre-wrap; font-size: 13px; }
.right h3 { margin: 0 0 12px; }
.row { display: flex; gap: 8px; margin-bottom: 10px; }
.row select { width: 140px; }
.submit-msg { margin-top: 10px; color: var(--primary); font-size: 13px; }
.latest { margin-top: 14px; border-top: 1px solid var(--border); padding-top: 12px; }
.latest-head { font-weight: 600; margin-bottom: 6px; }
.err-pre { background: #1e1e1e; color: #f48771; padding: 10px; border-radius: 6px; white-space: pre-wrap; font-size: 12px; margin-top: 8px; }
</style>
