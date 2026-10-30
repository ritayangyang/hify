<script setup lang="ts">
import AgentList from './agents/AgentList.vue'
</script>

<template>
  <div class="agents-view">
    <div class="agents-view__header">
      <h2>Agent 管理</h2>
      <p>配置 Agent 名称、系统提示词，绑定对话模型（知识库与 MCP 工具关联随对应模块上线后开放）</p>
    </div>
    <el-card shadow="never">
      <AgentList />
    </el-card>
  </div>
</template>

<style scoped>
.agents-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.agents-view__header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--hify-text-1);
}

.agents-view__header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--hify-text-3);
}
</style>
