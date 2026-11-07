<template>
  <div>
    <div class="head">
      <h2>题目管理</h2>
      <button class="btn" @click="openCreate">+ 新建题目</button>
    </div>

    <div class="card" v-if="!editing">
      <table class="tbl">
        <thead>
          <tr>
            <th style="width: 60px">#</th>
            <th>标题</th>
            <th style="width: 90px">难度</th>
            <th style="width: 100px">通过/提交</th>
            <th style="width: 160px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in list" :key="p.id">
            <td>{{ p.id }}</td>
            <td>{{ p.title }}</td>
            <td><span class="tag" :class="diffClass(p.difficulty)">{{ diffText(p.difficulty) }}</span></td>
            <td>{{ p.acceptCount }} / {{ p.submitCount }}</td>
            <td>
              <button class="btn ghost sm" @click="openEdit(p.id)">编辑</button>
              <button class="btn danger sm" @click="onDelete(p.id)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="5" class="empty">还没有题目，点右上角新建</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="card" v-else>
      <h3>{{ form.id ? '编辑题目 #' + form.id : '新建题目' }}</h3>
      <div class="form-grid">
        <label>标题<input class="input" v-model="form.title" /></label>
        <label>难度
          <select v-model.number="form.difficulty">
            <option :value="1">简单</option>
            <option :value="2">中等</option>
            <option :value="3">困难</option>
          </select>
        </label>
        <label>时间限制(ms)<input class="input" type="number" v-model.number="form.timeLimit" /></label>
        <label>内存限制(MB)<input class="input" type="number" v-model.number="form.memoryLimit" /></label>
      </div>
      <label>题面（Markdown）<textarea class="input" v-model="form.content" rows="6"></textarea></label>

      <div class="cases">
        <div class="cases-head">
          <span>测试用例</span>
          <button class="btn ghost sm" @click="addCase">+ 添加用例</button>
        </div>
        <div class="case-row" v-for="(c, i) in form.cases" :key="i">
          <textarea class="input" v-model="c.input" rows="2" placeholder="输入"></textarea>
          <textarea class="input" v-model="c.output" rows="2" placeholder="期望输出"></textarea>
          <label class="sm"><input type="checkbox" :checked="c.isSample === 1" @change="c.isSample = ($event.target as HTMLInputElement).checked ? 1 : 0" /> 示例</label>
          <input class="input score" type="number" v-model.number="c.score" placeholder="分" />
          <button class="btn danger sm" @click="form.cases.splice(i, 1)">×</button>
        </div>
      </div>

      <div class="actions">
        <button class="btn" :disabled="saving" @click="onSave">保存</button>
        <button class="btn ghost" @click="editing = false">取消</button>
      </div>
      <div v-if="msg" class="msg">{{ msg }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import {
  adminProblemList,
  adminProblemDetail,
  createProblem,
  updateProblem,
  deleteProblem,
} from '@/api/problem';
import type { AdminProblemDetailVO, ProblemSaveDTO, TestCaseDTO } from '@/types';

const list = ref<AdminProblemDetailVO[]>([]);
const editing = ref(false);
const saving = ref(false);
const msg = ref('');
const form = reactive<ProblemSaveDTO & { id?: number }>({
  id: undefined,
  title: '',
  content: '',
  difficulty: 1,
  timeLimit: 1000,
  memoryLimit: 256,
  cases: [],
});

function diffText(d: number) {
  return d === 1 ? '简单' : d === 2 ? '中等' : '困难';
}
function diffClass(d: number) {
  return d === 1 ? 'easy' : d === 2 ? 'medium' : 'hard';
}

function resetForm() {
  form.id = undefined;
  form.title = '';
  form.content = '';
  form.difficulty = 1;
  form.timeLimit = 1000;
  form.memoryLimit = 256;
  form.cases = [];
}
function addCase() {
  form.cases.push({ input: '', output: '', isSample: 1, score: 50 });
}

async function reload() {
  list.value = await adminProblemList();
}
function openCreate() {
  resetForm();
  editing.value = true;
  msg.value = '';
}
async function openEdit(id: number) {
  const d = await adminProblemDetail(id);
  form.id = d.id;
  form.title = d.title;
  form.content = d.content;
  form.difficulty = d.difficulty;
  form.timeLimit = d.timeLimit;
  form.memoryLimit = d.memoryLimit;
  form.cases = d.cases.map<TestCaseDTO>((c) => ({
    input: c.input,
    output: c.output,
    isSample: c.isSample,
    score: c.score,
  }));
  editing.value = true;
  msg.value = '';
}
async function onSave() {
  if (!form.title.trim()) {
    msg.value = '请填写标题';
    return;
  }
  saving.value = true;
  try {
    const dto: ProblemSaveDTO = {
      title: form.title,
      content: form.content,
      difficulty: form.difficulty,
      timeLimit: form.timeLimit,
      memoryLimit: form.memoryLimit,
      cases: form.cases,
    };
    if (form.id) {
      await updateProblem(form.id, dto);
    } else {
      await createProblem(dto);
    }
    msg.value = '保存成功';
    editing.value = false;
    await reload();
  } catch (e) {
    msg.value = (e as Error).message;
  } finally {
    saving.value = false;
  }
}
async function onDelete(id: number) {
  if (!confirm(`确认删除题目 #${id}？`)) return;
  await deleteProblem(id);
  await reload();
}

onMounted(reload);
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.tbl { width: 100%; border-collapse: collapse; }
.tbl th, .tbl td { text-align: left; padding: 10px 8px; border-bottom: 1px solid var(--border); }
.empty { text-align: center; color: var(--muted); padding: 24px; }
.btn.sm { padding: 4px 10px; font-size: 12px; }
.btn.danger { background: var(--danger); }
.btn.danger:hover { background: #b02440; }
.form-grid { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr; gap: 10px; margin-bottom: 10px; }
.form-grid label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); }
label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--muted); margin-bottom: 10px; }
.cases { margin: 12px 0; }
.cases-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; margin-bottom: 8px; }
.case-row { display: grid; grid-template-columns: 1fr 1fr auto 80px auto; gap: 8px; align-items: center; margin-bottom: 8px; }
.case-row .sm { flex-direction: row; align-items: center; gap: 4px; }
.score { width: 80px; }
.actions { display: flex; gap: 10px; margin-top: 10px; }
.msg { margin-top: 8px; color: var(--success); font-size: 13px; }
</style>
