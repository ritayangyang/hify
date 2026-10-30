<script setup lang="ts">
import WorkflowList from './workflows/WorkflowList.vue'
</script>

<template>
  <div class="workflows-view">
    <div class="workflows-view__header">
      <h2>工作流</h2>
      <p>顺序节点编排：开始 → LLM → 条件分支 → 工具调用 / 知识检索 → 结束（当前以 JSON 编辑图定义）</p>
    </div>
    <el-card shadow="never">
      <WorkflowList />
    </el-card>
  </div>
</template>

<style scoped>
.workflows-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.workflows-view__header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--hify-text-1);
}

.workflows-view__header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--hify-text-3);
}
</style>
