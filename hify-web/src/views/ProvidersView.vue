<script setup lang="ts">
import ProviderList from './providers/ProviderList.vue'
</script>

<template>
  <div class="providers-view">
    <div class="providers-view__header">
      <h2>模型管理</h2>
      <p>管理 LLM 提供商配置、连通性与健康状态（火山方舟豆包 / OpenAI / Anthropic / Ollama）</p>
    </div>
    <el-card shadow="never">
      <ProviderList />
    </el-card>
  </div>
</template>

<style scoped>
.providers-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.providers-view__header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--hify-text-1);
}

.providers-view__header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--hify-text-3);
}
</style>
