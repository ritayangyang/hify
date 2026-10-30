<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import { useAppStore } from '@/stores/app'

const route = useRoute()
const appStore = useAppStore()

// 高亮当前路由对应的菜单项
const activeMenu = computed(() => route.path)
</script>

<template>
  <el-config-provider :locale="zhCn">
    <el-container class="app-layout">
      <el-aside :width="`var(--hify-sidebar-width)`" class="app-aside">
        <div class="app-logo">{{ appStore.appName }}</div>

        <el-menu
          router
          :default-active="activeMenu"
          background-color="var(--hify-sidebar-bg)"
          text-color="var(--hify-sidebar-text)"
          active-text-color="var(--hify-sidebar-text-active)"
        >
          <el-menu-item index="/providers">
            <el-icon><Cpu /></el-icon>
            <span>模型管理</span>
          </el-menu-item>
          <el-menu-item index="/agents">
            <el-icon><Avatar /></el-icon>
            <span>Agent 管理</span>
          </el-menu-item>
          <el-menu-item index="/workflows">
            <el-icon><Share /></el-icon>
            <span>工作流</span>
          </el-menu-item>
          <el-menu-item index="/chat">
            <el-icon><ChatDotRound /></el-icon>
            <span>对话</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-container>
        <el-main class="app-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </el-config-provider>
</template>

<style scoped>
.app-layout {
  height: 100vh;
}

.app-aside {
  background-color: var(--hify-sidebar-bg);
  border-right: 1px solid var(--hify-sidebar-border);
  overflow-x: hidden;
}

.app-logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: var(--hify-text-inverse);
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 2px;
  background: var(--hify-gradient-brand);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.app-aside :deep(.el-menu) {
  border-right: none;
}

.app-aside :deep(.el-menu-item) {
  transition: var(--hify-transition-color);
}

.app-aside :deep(.el-menu-item:hover) {
  background-color: var(--hify-sidebar-item-hover);
  color: var(--hify-sidebar-text-hover);
}

.app-aside :deep(.el-menu-item.is-active) {
  background-color: var(--hify-sidebar-item-active);
  box-shadow: var(--hify-sidebar-item-active-glow);
}

.app-main {
  background-color: var(--hify-bg-canvas);
  padding: 20px;
}
</style>
