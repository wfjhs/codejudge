<template>
  <textarea
    class="editor"
    :value="modelValue"
    :placeholder="placeholder"
    spellcheck="false"
    @input="$emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
  ></textarea>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: string;
  placeholder?: string;
}>();
defineEmits<{ 'update:modelValue': [value: string] }>();
</script>

<style scoped>
.editor {
  width: 100%;
  min-height: 240px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  line-height: 1.5;
  tab-size: 4;
  resize: vertical;
  background: #1e1e1e;
  color: #d4d4d4;
  border: 1px solid #333;
  border-radius: 8px;
  padding: 12px;
}
</style>
