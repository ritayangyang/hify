<script setup lang="ts">
import { reactive, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { CONFIRM_CANCELLED, useConfirm } from '@/components/useConfirm'
import type { FormDialogMode, HifyColumn, PageParams } from '@/components/types'
import {
  createProvider,
  deleteProvider,
  getProviderList,
  testConnection,
  updateProvider,
} from '@/api/provider'
import type {
  ProviderHealthStatus,
  ProviderListItem,
  ProviderStatus,
  ProviderTypeName,
} from '@/api/provider'

/* ---------------- 类型选项与展示映射 ---------------- */

const TYPE_OPTIONS: { label: string; value: ProviderTypeName }[] = [
  { label: 'OpenAI', value: 'OPENAI' },
  { label: 'OpenAI 兼容（火山方舟/DeepSeek 等）', value: 'OPENAI_COMPATIBLE' },
  { label: 'Anthropic', value: 'ANTHROPIC' },
  { label: 'Ollama', value: 'OLLAMA' },
]

function typeName(code: number): ProviderTypeName {
  return TYPE_OPTIONS[code - 1]?.value ?? 'OPENAI'
}

function typeLabel(code: number): string {
  return TYPE_OPTIONS[code - 1]?.label ?? String(code)
}

const HEALTH_TAG: Record<
  ProviderHealthStatus,
  { tag: 'success' | 'danger' | 'warning' | 'info'; text: string }
> = {
  UP: { tag: 'success', text: 'UP' },
  DOWN: { tag: 'danger', text: 'DOWN' },
  DEGRADED: { tag: 'warning', text: 'DEGRADED' },
  UNKNOWN: { tag: 'info', text: 'UNKNOWN' },
}

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

/** 健康状态展示元信息；未知/缺省值降级为灰色 UNKNOWN，避免渲染期整表崩溃 */
function healthMeta(status: ProviderHealthStatus | undefined | null) {
  return (status && HEALTH_TAG[status]) || HEALTH_TAG.UNKNOWN
}

/* ---------------- 列表 ---------------- */

/**
 * 泛型 SFC 的实例无法用 InstanceType/ComponentExposed 取类型，
 * 用回调 ref 注册，只保留 defineExpose 暴露方法的结构化类型。
 */
const tableApi = shallowRef<{
  refresh: () => Promise<void>
  reset: () => Promise<void>
}>()

function registerTable(el: unknown) {
  tableApi.value = (el ?? undefined) as typeof tableApi.value
}

/** 筛选条件（HifyTable 回调给的是 {page,size}，这里适配成 getProviderList 的入参） */
const filters = reactive<{ type: ProviderTypeName | ''; enabled: boolean | '' }>({
  type: '',
  enabled: '',
})

function fetchProviders(params: PageParams) {
  return getProviderList({
    page: params.page,
    pageSize: params.size,
    type: filters.type === '' ? undefined : filters.type,
    enabled: filters.enabled === '' ? undefined : filters.enabled,
  })
}

const columns: HifyColumn<ProviderListItem>[] = [
  { label: '提供商', slot: 'name', minWidth: 180 },
  { label: '类型', slot: 'type', width: 130 },
  { label: 'API 地址', prop: 'baseUrl', minWidth: 220 },
  { label: '模型数', prop: 'enabledModelCount', width: 90, align: 'center' },
  { label: '健康状态', slot: 'health', width: 130 },
  { label: '状态', slot: 'status', width: 90, align: 'center' },
  { label: '更新时间', slot: 'updatedAt', width: 170 },
  { label: '操作', slot: 'actions', width: 220, fixed: 'right', align: 'center' },
]

/* ---------------- 新建 / 编辑弹窗 ---------------- */

interface ProviderFormState {
  code: string
  name: string
  type: ProviderTypeName
  baseUrl: string
  apiKey: string
  status: ProviderStatus
}

function defaultForm(): ProviderFormState {
  // 默认按团队实际使用的火山方舟（OpenAI 兼容协议）预填
  return { code: '', name: '', type: 'OPENAI_COMPATIBLE', baseUrl: '', apiKey: '', status: 1 }
}

/** 弹窗实例只需要 open/close（泛型 D 在本页固定为 ProviderListItem） */
const dialogApi = shallowRef<{
  open: (data?: ProviderListItem) => void
  close: () => void
}>()

function registerDialog(el: unknown) {
  dialogApi.value = (el ?? undefined) as typeof dialogApi.value
}
const dialogVisible = ref(false)
const submitting = ref(false)

/** el-form 实例同样用回调 ref，避免字符串 ref 对泛型组件实例的窄化 */
const formApi = shallowRef<FormInstance>()

function registerForm(el: unknown) {
  formApi.value = (el ?? undefined) as FormInstance | undefined
}

const form = reactive<ProviderFormState>(defaultForm())
const editingId = ref<number | null>(null)

const rules: FormRules<ProviderFormState> = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 API 地址', trigger: 'blur' }],
}

/** 弹窗组件泛型 D 未绑定，事件 data 为 unknown，入口处收窄 */
interface DialogEvent {
  mode: FormDialogMode
  data?: unknown
}

function handleOpen(payload: DialogEvent) {
  Object.assign(form, defaultForm())
  formApi.value?.clearValidate()
  editingId.value = null
  if (payload.mode === 'edit' && payload.data) {
    const row = payload.data as ProviderListItem
    editingId.value = row.id
    form.code = row.code
    form.name = row.name
    form.type = typeName(row.type)
    form.baseUrl = row.baseUrl
    form.status = row.status
    // 出参 apiKey 已脱敏为 ******，编辑时留空表示不修改
    form.apiKey = ''
  }
}

async function handleSubmit(payload: DialogEvent) {
  if (!formApi.value) return
  const valid = await formApi.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (payload.mode === 'create') {
      await createProvider({
        ...(form.code.trim() ? { code: form.code.trim() } : {}),
        type: form.type,
        name: form.name.trim(),
        baseUrl: form.baseUrl.trim(),
        ...(form.apiKey ? { authConfig: { apiKey: form.apiKey } } : {}),
        status: form.status,
      })
      ElMessage.success('创建成功')
    } else {
      const fallbackId = (payload.data as ProviderListItem | undefined)?.id
      await updateProvider(editingId.value ?? fallbackId!, {
        type: form.type,
        name: form.name.trim(),
        baseUrl: form.baseUrl.trim(),
        status: form.status,
        // apiKey 留空不下发，后端保留原值
        ...(form.apiKey ? { authConfig: { apiKey: form.apiKey } } : {}),
      })
      ElMessage.success('更新成功')
    }
    dialogApi.value?.close()
    tableApi.value?.refresh()
  } catch {
    // 错误提示已由 request 拦截器统一弹出，保持弹窗打开便于修改
  } finally {
    submitting.value = false
  }
}

/* ---------------- 删除 ---------------- */

const removeProvider = useConfirm(deleteProvider, {
  title: '删除提供商',
  text: '将同时逻辑删除其模型配置并清除健康记录，确定删除吗？',
  confirmText: '删除',
  successText: '删除成功',
})

async function handleDelete(row: ProviderListItem) {
  const result = await removeProvider(row.id).catch(() => null)
  // void 删除接口成功时返回值也是 null，必须用取消哨兵区分"取消"和"删除成功"
  if (result !== CONFIRM_CANCELLED) {
    tableApi.value?.refresh()
  }
}

/* ---------------- 连通性测试 ---------------- */

const testingId = ref<number | null>(null)

async function handleTestConnection(row: ProviderListItem) {
  testingId.value = row.id
  try {
    const result = await testConnection(row.id)
    if (result.success) {
      ElMessage.success(`连通正常 · ${result.modelCount} 个模型 · ${result.latencyMs}ms`)
    } else {
      ElMessage.error(`连接失败：${result.errorMessage ?? '未知错误'}`)
    }
    // 回写健康状态后刷新列表的健康列
    tableApi.value?.refresh()
  } catch {
    // 404/网络错误已由 request 拦截器提示
  } finally {
    testingId.value = null
  }
}
</script>

<template>
  <div class="provider-list">
    <div class="provider-list__toolbar">
      <el-select
        v-model="filters.type"
        placeholder="全部类型"
        clearable
        class="provider-list__filter"
      >
        <el-option
          v-for="option in TYPE_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-select
        v-model="filters.enabled"
        placeholder="全部状态"
        clearable
        class="provider-list__filter provider-list__filter--status"
      >
        <el-option label="启用" :value="true" />
        <el-option label="禁用" :value="false" />
      </el-select>
      <el-button @click="tableApi?.reset()">查询</el-button>
      <span class="provider-list__spacer" />
      <el-button type="primary" @click="dialogApi?.open()">新建提供商</el-button>
    </div>

    <HifyTable
      :ref="registerTable"
      :columns="columns"
      :fetch-api="fetchProviders"
      :page-size="10"
      row-key="id"
    >
      <template #name="{ row }">
        <div class="provider-cell">
          <span class="provider-cell__name">{{ row.name }}</span>
          <span class="provider-cell__code">{{ row.code }}</span>
        </div>
      </template>

      <template #type="{ row }">
        <el-tag type="info" effect="plain">{{ typeLabel(row.type) }}</el-tag>
      </template>

      <template #health="{ row }">
        <!-- 不用 el-tooltip：其 popper 异步重渲染动态转发插槽时会丢失 row，导致整表白屏 -->
        <el-tag
          :type="healthMeta(row.healthStatus).tag"
          effect="light"
          :title="`连续失败 ${row.failCount} 次 · 延迟 ${row.latencyMs ?? '-'}ms · 最近成功 ${formatTime(row.lastSuccessAt)}`"
        >
          {{ healthMeta(row.healthStatus).text }}
        </el-tag>
      </template>

      <template #status="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain">
          {{ row.status === 1 ? '启用' : '禁用' }}
        </el-tag>
      </template>

      <template #updatedAt="{ row }">
        {{ formatTime(row.updatedAt) }}
      </template>

      <template #actions="{ row }">
        <el-button link type="primary" size="small" @click="dialogApi?.open(row)">编辑</el-button>
        <el-button
          link
          type="primary"
          size="small"
          :loading="testingId === row.id"
          @click="handleTestConnection(row)"
        >
          连通性测试
        </el-button>
        <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
      </template>
    </HifyTable>

    <HifyFormDialog
      :ref="registerDialog"
      v-model="dialogVisible"
      create-title="新建提供商"
      edit-title="编辑提供商"
      :submitting="submitting"
      width="560"
      @open="handleOpen"
      @submit="handleSubmit"
    >
      <el-form
        :ref="registerForm"
        :model="form"
        :rules="rules"
        label-width="92px"
        class="provider-form"
      >
        <el-form-item label="编码" prop="code">
          <el-input
            v-model="form.code"
            placeholder="留空则按类型自动生成，如 openai_compatible；多个时自定义，如 ark-backup"
            :disabled="editingId !== null"
          />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" class="provider-form__control">
            <el-option
              v-for="option in TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 火山方舟" />
        </el-form-item>
        <el-form-item label="API 地址" prop="baseUrl">
          <el-input
            v-model="form.baseUrl"
            placeholder="https://ark.cn-beijing.volces.com/api/v3"
          />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editingId === null ? '火山方舟 API Key；Ollama 等无认证类型可留空' : '留空表示不修改已保存的 Key'"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
    </HifyFormDialog>
  </div>
</template>

<style scoped>
.provider-list__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.provider-list__filter {
  width: 240px;
}

.provider-list__filter--status {
  width: 130px;
}

.provider-list__spacer {
  flex: 1;
}

.provider-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.provider-cell__name {
  font-weight: 600;
  color: var(--hify-text-1);
}

.provider-cell__code {
  font-size: 12px;
  color: var(--hify-text-3);
}

.provider-form__control {
  width: 100%;
}
</style>
