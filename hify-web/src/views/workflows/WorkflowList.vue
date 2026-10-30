<script setup lang="ts">
import { reactive, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { CONFIRM_CANCELLED, useConfirm } from '@/components/useConfirm'
import type { FormDialogMode, HifyColumn, PageParams } from '@/components/types'
import { getChatModelOptions } from '@/api/modelConfig'
import {
  createWorkflow,
  deleteWorkflow,
  getWorkflowDetail,
  getWorkflowList,
  updateWorkflow,
  type WorkflowGraph,
  type WorkflowListItem,
  type WorkflowStatus,
} from '@/api/workflow'

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

function fetchWorkflows(params: PageParams) {
  return getWorkflowList({
    page: params.page,
    pageSize: params.size,
    name: filters.name === '' ? undefined : filters.name,
    enabled: filters.enabled === '' ? undefined : filters.enabled,
  })
}

const columns: HifyColumn<WorkflowListItem>[] = [
  { label: '工作流', slot: 'name', minWidth: 220 },
  { label: '节点数', prop: 'nodeCount', width: 90, align: 'center' },
  { label: '连线数', prop: 'edgeCount', width: 90, align: 'center' },
  { label: '状态', slot: 'status', width: 90, align: 'center' },
  { label: '更新时间', slot: 'updatedAt', width: 170 },
  { label: '操作', slot: 'actions', width: 140, fixed: 'right', align: 'center' },
]

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

/** 状态映射兜底，避免渲染期异常白屏 */
function statusTagType(status: WorkflowStatus | undefined) {
  return status === 1 ? 'success' : 'info'
}

/* ---------------- 新建 / 编辑弹窗 ---------------- */

interface WorkflowFormState {
  name: string
  description: string
  status: WorkflowStatus
  graphText: string
}

function defaultForm(): WorkflowFormState {
  return { name: '', description: '', status: 1, graphText: '' }
}

const dialogApi = shallowRef<{
  open: (data?: WorkflowListItem) => void
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

const form = reactive<WorkflowFormState>(defaultForm())
const editingId = ref<number | null>(null)

const rules: FormRules<WorkflowFormState> = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  graphText: [{ required: true, message: '图定义 JSON 不能为空', trigger: 'blur' }],
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

  if (payload.mode === 'create') {
    // 与模型管理新建预填默认值一致：打开即看到可用示例，降低上手门槛
    await fillTemplate()
    return
  }

  if (payload.mode === 'edit' && payload.data) {
    const row = payload.data as WorkflowListItem
    editingId.value = row.id
    // 图必须走详情接口回填（列表不含 nodes/edges）
    const detail = await getWorkflowDetail(row.id).catch(() => null)
    if (!detail) return
    form.name = detail.workflow.name
    form.description = detail.workflow.description ?? ''
    form.status = detail.workflow.status
    form.graphText = JSON.stringify(
      { nodes: detail.nodes, edges: detail.edges } satisfies WorkflowGraph,
      null,
      2,
    )
  }
}

/** 最小可用图模板（START → LLM → END） */
function buildTemplateGraph(modelConfigId: number): WorkflowGraph {
  return {
    nodes: [
      { id: 'start', type: 'START', config: { inputs: ['userMessage'] }, position: { x: 80, y: 160 } },
      {
        id: 'n1',
        type: 'LLM',
        config: {
          modelConfigId,
          promptTemplate: '请回答用户问题：{{start.userMessage}}',
          outputKey: 'answer',
          temperature: 0.7,
        },
        position: { x: 340, y: 160 },
      },
      { id: 'end', type: 'END', config: { replyTemplate: '{{n1.answer}}' }, position: { x: 600, y: 160 } },
    ],
    edges: [
      { source: 'start', target: 'n1' },
      { source: 'n1', target: 'end' },
    ],
  }
}

/** 填入最小可用图模板（START → LLM → END），模型 id 取第一个已启用对话模型 */
async function fillTemplate() {
  const options = await getChatModelOptions().catch(() => [])
  const modelConfigId = options[0]?.id ?? 6
  form.graphText = JSON.stringify(buildTemplateGraph(modelConfigId), null, 2)
}

/** 格式化按钮：美化 JSON；语法/结构非法时就地提示且不改动原文 */
function formatGraphText() {
  if (!form.graphText.trim()) {
    ElMessage.warning('图定义为空，可先填入最小模板')
    return
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(form.graphText)
  } catch (e) {
    ElMessage.error('JSON 语法错误，无法格式化：' + (e as Error).message)
    return
  }
  const graph = parsed as Partial<WorkflowGraph>
  if (!Array.isArray(graph.nodes) || !Array.isArray(graph.edges)) {
    ElMessage.error('图 JSON 必须包含 nodes 和 edges 两个数组')
    return
  }
  form.graphText = JSON.stringify(parsed, null, 2)
  ElMessage.success(`已格式化（${graph.nodes.length} 个节点 / ${graph.edges.length} 条连线）`)
}

/** 解析图 JSON；结构错误就地提示并返回 null */
function parseGraphText(): WorkflowGraph | null {
  let parsed: unknown
  try {
    parsed = JSON.parse(form.graphText)
  } catch (e) {
    ElMessage.error('图 JSON 解析失败：' + (e as Error).message)
    return null
  }
  const graph = parsed as Partial<WorkflowGraph>
  if (!Array.isArray(graph.nodes) || !Array.isArray(graph.edges)) {
    ElMessage.error('图 JSON 必须包含 nodes 和 edges 两个数组')
    return null
  }
  return { nodes: graph.nodes, edges: graph.edges }
}

async function handleSubmit(payload: DialogEvent) {
  if (!formApi.value) return
  const valid = await formApi.value.validate().catch(() => false)
  if (!valid) return
  const graph = parseGraphText()
  if (!graph) return

  submitting.value = true
  try {
    if (payload.mode === 'create') {
      await createWorkflow({
        name: form.name.trim(),
        description: form.description.trim() || null,
        status: form.status,
        nodes: graph.nodes,
        edges: graph.edges,
      })
      ElMessage.success('创建成功')
    } else {
      const fallbackId = (payload.data as WorkflowListItem | undefined)?.id
      await updateWorkflow(editingId.value ?? fallbackId!, {
        name: form.name.trim(),
        description: form.description.trim() || null,
        status: form.status,
        nodes: graph.nodes,
        edges: graph.edges,
      })
      ElMessage.success('更新成功')
    }
    dialogApi.value?.close()
    tableApi.value?.refresh()
  } catch {
    // 错误提示已由 request 拦截器统一弹出（图校验错误会精确到节点），保持弹窗打开便于修改
  } finally {
    submitting.value = false
  }
}

/* ---------------- 删除 ---------------- */

const removeWorkflow = useConfirm(deleteWorkflow, {
  title: '删除工作流',
  text: '将同时逻辑删除其全部节点和连线，确定删除吗？',
  confirmText: '删除',
  successText: '删除成功',
})

async function handleDelete(row: WorkflowListItem) {
  const result = await removeWorkflow(row.id).catch(() => null)
  // void 删除接口成功返回值也是 null，必须用取消哨兵区分
  if (result !== CONFIRM_CANCELLED) {
    tableApi.value?.refresh()
  }
}
</script>

<template>
  <div class="workflow-list">
    <div class="workflow-list__toolbar">
      <el-input
        v-model="filters.name"
        placeholder="按名称搜索"
        clearable
        class="workflow-list__filter-name"
        @keyup.enter="tableApi?.reset()"
        @clear="tableApi?.reset()"
      />
      <el-select
        v-model="filters.enabled"
        placeholder="全部状态"
        clearable
        class="workflow-list__filter-status"
      >
        <el-option label="启用" :value="true" />
        <el-option label="禁用" :value="false" />
      </el-select>
      <el-button @click="tableApi?.reset()">查询</el-button>
      <span class="workflow-list__spacer" />
      <el-button type="primary" @click="dialogApi?.open()">新建工作流</el-button>
    </div>

    <HifyTable
      :ref="registerTable"
      :columns="columns"
      :fetch-api="fetchWorkflows"
      :page-size="10"
      row-key="id"
    >
      <template #name="{ row }">
        <div class="workflow-cell">
          <span class="workflow-cell__name">{{ row.name }}</span>
          <span class="workflow-cell__desc">{{ row.description || '暂无简介' }}</span>
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
      create-title="新建工作流"
      edit-title="编辑工作流"
      :submitting="submitting"
      width="820"
      @open="handleOpen"
      @submit="handleSubmit"
    >
      <el-form
        :ref="registerForm"
        :model="form"
        :rules="rules"
        label-width="92px"
        class="workflow-form"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：客服意图分流" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" placeholder="一句话描述这个工作流的用途" />
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
        <el-form-item label="图定义 JSON" prop="graphText" class="workflow-form__graph-item">
          <div class="workflow-form__graph">
            <el-alert type="info" :closable="false" class="workflow-form__tip">
              <template #default>
                节点类型 START / LLM / CONDITION / TOOL / KNOWLEDGE / END；条件分支出口在
                edge.sourceHandle 填分支 id 或 default。保存时后端会校验起止节点、连线端点、环路等。
              </template>
            </el-alert>
            <div class="workflow-form__graph-actions">
              <el-button link type="primary" size="small" @click="fillTemplate">
                填入最小模板（START → LLM → END）
              </el-button>
              <el-button link type="primary" size="small" @click="formatGraphText">
                格式化
              </el-button>
            </div>
            <el-input
              v-model="form.graphText"
              type="textarea"
              :rows="20"
              spellcheck="false"
              placeholder='{"nodes":[...],"edges":[...]}'
              class="workflow-form__editor"
            />
          </div>
        </el-form-item>
      </el-form>
    </HifyFormDialog>
  </div>
</template>

<style scoped>
.workflow-list__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.workflow-list__filter-name {
  width: 220px;
}

.workflow-list__filter-status {
  width: 130px;
}

.workflow-list__spacer {
  flex: 1;
}

.workflow-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.workflow-cell__name {
  font-weight: 600;
  color: var(--hify-text-1);
}

.workflow-cell__desc {
  font-size: 12px;
  color: var(--hify-text-3);
}

.workflow-form__graph-item :deep(.el-form-item__content) {
  display: block;
}

.workflow-form__graph {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.workflow-form__tip {
  line-height: 1.5;
}

.workflow-form__graph-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
}

.workflow-form__editor :deep(textarea) {
  font-family: 'JetBrains Mono', 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}
</style>
