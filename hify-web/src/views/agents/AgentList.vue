<script setup lang="ts">
import { onMounted, reactive, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { CONFIRM_CANCELLED, useConfirm } from '@/components/useConfirm'
import type { FormDialogMode, HifyColumn, PageParams } from '@/components/types'
import { getChatModelOptions, type ModelOption } from '@/api/modelConfig'
import { getWorkflowOptions, type WorkflowOption } from '@/api/workflow'
import {
  createAgent,
  deleteAgent,
  getAgentDetail,
  getAgentList,
  updateAgent,
  type AgentListItem,
  type AgentModelParams,
  type AgentStatus,
} from '@/api/agent'

/* ---------------- 模型下拉 ---------------- */

const modelOptions = ref<ModelOption[]>([])
const modelLoading = ref(false)

async function loadModelOptions() {
  modelLoading.value = true
  try {
    modelOptions.value = await getChatModelOptions()
  } catch {
    // 错误提示已由 request 拦截器统一弹出
  } finally {
    modelLoading.value = false
  }
}

function modelLabel(option: ModelOption): string {
  return `${option.name}（${option.providerName}）`
}

onMounted(loadModelOptions)

/* ---------------- 工作流下拉 ---------------- */

const workflowOptions = ref<WorkflowOption[]>([])
const workflowLoading = ref(false)

async function loadWorkflowOptions() {
  workflowLoading.value = true
  try {
    workflowOptions.value = await getWorkflowOptions()
  } catch {
    // 错误提示已由 request 拦截器统一弹出
  } finally {
    workflowLoading.value = false
  }
}

/* ---------------- 列表 ---------------- */

/**
 * 泛型 SFC 的实例无法用 InstanceType 取类型，
 * 用回调 ref 注册，只保留 defineExpose 暴露方法的结构化类型。
 */
const tableApi = shallowRef<{
  refresh: () => Promise<void>
  reset: () => Promise<void>
}>()

function registerTable(el: unknown) {
  tableApi.value = (el ?? undefined) as typeof tableApi.value
}

const filters = reactive<{ name: string; enabled: boolean | '' }>({
  name: '',
  enabled: '',
})

function fetchAgents(params: PageParams) {
  return getAgentList({
    page: params.page,
    pageSize: params.size,
    name: filters.name === '' ? undefined : filters.name,
    enabled: filters.enabled === '' ? undefined : filters.enabled,
  })
}

const columns: HifyColumn<AgentListItem>[] = [
  { label: 'Agent', slot: 'name', minWidth: 200 },
  { label: '绑定模型', slot: 'model', minWidth: 200 },
  { label: '知识库', prop: 'knowledgeCount', width: 90, align: 'center' },
  { label: 'MCP 工具', prop: 'mcpCount', width: 90, align: 'center' },
  { label: '状态', slot: 'status', width: 90, align: 'center' },
  { label: '更新时间', slot: 'updatedAt', width: 170 },
  { label: '操作', slot: 'actions', width: 140, fixed: 'right', align: 'center' },
]

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

/** 状态映射加空值兜底，避免渲染期异常导致整页白屏 */
function statusTagType(status: AgentStatus | undefined) {
  return status === 1 ? 'success' : 'info'
}

/* ---------------- 新建 / 编辑弹窗 ---------------- */

interface AgentFormState {
  name: string
  modelConfigId: number | undefined
  workflowId: number | null
  description: string
  systemPrompt: string
  temperature: number | undefined
  topP: number | undefined
  maxTokens: number | undefined
  status: AgentStatus
}

function defaultForm(): AgentFormState {
  return {
    name: '',
    modelConfigId: undefined,
    workflowId: null,
    description: '',
    systemPrompt: '',
    temperature: undefined,
    topP: undefined,
    maxTokens: undefined,
    status: 1,
  }
}

const dialogApi = shallowRef<{
  open: (data?: AgentListItem) => void
  close: () => void
}>()

function registerDialog(el: unknown) {
  dialogApi.value = (el ?? undefined) as typeof dialogApi.value
}
const dialogVisible = ref(false)
const submitting = ref(false)

const formApi = shallowRef<FormInstance>()

function registerForm(el: unknown) {
  formApi.value = (el ?? undefined) as FormInstance | undefined
}

const form = reactive<AgentFormState>(defaultForm())
const editingId = ref<number | null>(null)
/** 编辑时保留已有知识库/MCP 关联（本期无多选 UI，更新时原样回传避免被整体替换清空） */
const preservedKnowledgeIds = ref<number[]>([])
const preservedMcpToolIds = ref<number[]>([])

const rules: FormRules<AgentFormState> = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  modelConfigId: [{ required: true, message: '请选择绑定模型', trigger: 'change' }],
}

/** 弹窗组件泛型 D 未绑定，事件 data 为 unknown，入口处收窄 */
interface DialogEvent {
  mode: FormDialogMode
  data?: unknown
}

async function handleOpen(payload: DialogEvent) {
  Object.assign(form, defaultForm())
  formApi.value?.clearValidate()
  editingId.value = null
  preservedKnowledgeIds.value = []
  preservedMcpToolIds.value = []
  // 每次打开刷新模型/工作流下拉，拿到最新的可用项
  await Promise.all([loadModelOptions(), loadWorkflowOptions()])

  if (payload.mode === 'edit' && payload.data) {
    const row = payload.data as AgentListItem
    editingId.value = row.id
    // 详情含关联 id 全量列表与 modelParams，必须走详情接口回填
    const detail = await getAgentDetail(row.id).catch(() => null)
    const source = detail?.agent ?? row
    form.name = source.name
    form.modelConfigId = source.modelConfigId
    form.workflowId = source.workflowId ?? null
    form.description = source.description ?? ''
    form.systemPrompt = source.systemPrompt ?? ''
    form.status = source.status
    form.temperature = numericParam(source.modelParams?.temperature)
    form.topP = numericParam(source.modelParams?.topP)
    form.maxTokens = numericParam(source.modelParams?.maxTokens)
    preservedKnowledgeIds.value = detail?.knowledgeIds ?? []
    preservedMcpToolIds.value = detail?.mcpToolIds ?? []
  }
}

function numericParam(value: number | undefined): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function buildModelParams(): AgentModelParams | undefined {
  const params: AgentModelParams = {}
  if (form.temperature !== undefined) params.temperature = form.temperature
  if (form.topP !== undefined) params.topP = form.topP
  if (form.maxTokens !== undefined) params.maxTokens = form.maxTokens
  return Object.keys(params).length > 0 ? params : undefined
}

async function handleSubmit(payload: DialogEvent) {
  if (!formApi.value) return
  const valid = await formApi.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (payload.mode === 'create') {
      await createAgent({
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        systemPrompt: form.systemPrompt || undefined,
        modelConfigId: form.modelConfigId!,
        workflowId: form.workflowId,
        modelParams: buildModelParams(),
        status: form.status,
      })
      ElMessage.success('创建成功')
    } else {
      const fallbackRow = (payload.data as AgentListItem | undefined) ?? undefined
      await updateAgent(editingId.value ?? fallbackRow!.id, {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        systemPrompt: form.systemPrompt || undefined,
        modelConfigId: form.modelConfigId!,
        workflowId: form.workflowId,
        modelParams: buildModelParams(),
        status: form.status,
        knowledgeIds: preservedKnowledgeIds.value,
        mcpToolIds: preservedMcpToolIds.value,
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

const removeAgent = useConfirm(deleteAgent, {
  title: '删除 Agent',
  text: '将同时删除其知识库与 MCP 工具关联，确定删除吗？',
  confirmText: '删除',
  successText: '删除成功',
})

async function handleDelete(row: AgentListItem) {
  const result = await removeAgent(row.id).catch(() => null)
  // void 删除接口成功时返回值也是 null，必须用取消哨兵区分"取消"和"删除成功"
  if (result !== CONFIRM_CANCELLED) {
    tableApi.value?.refresh()
  }
}
</script>

<template>
  <div class="agent-list">
    <div class="agent-list__toolbar">
      <el-input
        v-model="filters.name"
        placeholder="按名称搜索"
        clearable
        class="agent-list__filter-name"
        @keyup.enter="tableApi?.reset()"
        @clear="tableApi?.reset()"
      />
      <el-select
        v-model="filters.enabled"
        placeholder="全部状态"
        clearable
        class="agent-list__filter-status"
      >
        <el-option label="启用" :value="true" />
        <el-option label="禁用" :value="false" />
      </el-select>
      <el-button @click="tableApi?.reset()">查询</el-button>
      <span class="agent-list__spacer" />
      <el-button type="primary" @click="dialogApi?.open()">新建 Agent</el-button>
    </div>

    <HifyTable
      :ref="registerTable"
      :columns="columns"
      :fetch-api="fetchAgents"
      :page-size="10"
      row-key="id"
    >
      <template #name="{ row }">
        <div class="agent-cell">
          <span class="agent-cell__name">{{ row.name }}</span>
          <span class="agent-cell__desc">{{ row.description || '暂无简介' }}</span>
        </div>
      </template>

      <template #model="{ row }">
        <div class="agent-cell">
          <span class="agent-cell__model">{{ row.modelName ?? '模型已删除' }}</span>
          <span class="agent-cell__provider">{{ row.providerName ?? '-' }}</span>
        </div>
      </template>

      <template #status="{ row }">
        <el-tag :type="statusTagType(row.status)" effect="plain">
          {{ row.status === 1 ? '启用' : '禁用' }}
        </el-tag>
      </template>

      <template #updatedAt="{ row }">
        {{ formatTime(row.updatedAt) }}
      </template>

      <template #actions="{ row }">
        <el-button link type="primary" size="small" @click="dialogApi?.open(row)">编辑</el-button>
        <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
      </template>
    </HifyTable>

    <HifyFormDialog
      :ref="registerDialog"
      v-model="dialogVisible"
      create-title="新建 Agent"
      edit-title="编辑 Agent"
      :submitting="submitting"
      width="600"
      @open="handleOpen"
      @submit="handleSubmit"
    >
      <el-form
        :ref="registerForm"
        :model="form"
        :rules="rules"
        label-width="92px"
        class="agent-form"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：售前客服助手" />
        </el-form-item>
        <el-form-item label="绑定模型" prop="modelConfigId">
          <el-select
            v-model="form.modelConfigId"
            class="agent-form__control"
            :loading="modelLoading"
            placeholder="选择已启用的对话模型"
          >
            <el-option
              v-for="option in modelOptions"
              :key="option.id"
              :label="modelLabel(option)"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定工作流">
          <el-select
            v-model="form.workflowId"
            class="agent-form__control"
            :loading="workflowLoading"
            placeholder="不绑定则为普通对话"
            clearable
          >
            <el-option
              v-for="option in workflowOptions"
              :key="option.id"
              :label="option.name"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" placeholder="一句话描述这个 Agent 的用途" />
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="如：你是专业的售前客服，回答简洁、礼貌"
          />
        </el-form-item>
        <el-form-item label="生成参数">
          <div class="agent-form__params">
            <el-input-number
              v-model="form.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              :precision="2"
              controls-position="right"
              placeholder="温度"
            />
            <el-input-number
              v-model="form.topP"
              :min="0"
              :max="1"
              :step="0.1"
              :precision="2"
              controls-position="right"
              placeholder="topP"
            />
            <el-input-number
              v-model="form.maxTokens"
              :min="1"
              :step="100"
              :precision="0"
              controls-position="right"
              placeholder="maxTokens"
            />
          </div>
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
.agent-list__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.agent-list__filter-name {
  width: 220px;
}

.agent-list__filter-status {
  width: 130px;
}

.agent-list__spacer {
  flex: 1;
}

.agent-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.agent-cell__name {
  font-weight: 600;
  color: var(--hify-text-1);
}

.agent-cell__desc,
.agent-cell__provider {
  font-size: 12px;
  color: var(--hify-text-3);
}

.agent-form__control {
  width: 100%;
}

.agent-form__params {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
