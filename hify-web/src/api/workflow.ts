import { del, get, post, put } from '@/utils/request'
import type { PagedData } from '@/components/types'

/* ------------------------------------------------------------------ */
/* 类型定义（与后端 com.hify.modules.workflow.api 对齐）                */
/* ------------------------------------------------------------------ */

/** 节点类型，与后端 NodeType 枚举名一致 */
export type NodeType = 'START' | 'LLM' | 'CONDITION' | 'TOOL' | 'KNOWLEDGE' | 'END'

/** 启用状态：1 启用，0 禁用 */
export type WorkflowStatus = 0 | 1

/** 画布坐标（后端原样存取，不解释） */
export interface NodePosition {
  x: number
  y: number
}

/**
 * 节点：config 按 type 多态，前端用宽松 JSON 对象承载，
 * 具体字段约束在后端 NodeConfigParser 校验。
 */
export interface WorkflowNodeData {
  id: string
  type: NodeType
  config?: Record<string, unknown> | null
  position?: NodePosition | null
}

/** 条件节点的 sourceHandle 对应分支 id 或 "default"；普通节点出线为 null */
export interface WorkflowEdgeData {
  id?: string | null
  source: string
  sourceHandle?: string | null
  target: string
}

/** 完整图定义（nodes + edges） */
export interface WorkflowGraph {
  nodes: WorkflowNodeData[]
  edges: WorkflowEdgeData[]
}

/** 与后端 WorkflowDto 对齐 */
export interface WorkflowVO {
  id: number
  name: string
  description: string | null
  status: WorkflowStatus
  createdAt: string
  updatedAt: string
}

/** 与后端 WorkflowListItemDto 对齐 */
export interface WorkflowListItem extends WorkflowVO {
  nodeCount: number
  edgeCount: number
}

/** 与后端 WorkflowDetailDto 对齐 */
export interface WorkflowDetail {
  workflow: WorkflowVO
  nodes: WorkflowNodeData[]
  edges: WorkflowEdgeData[]
}

/** 创建/更新入参：完整请求体，更新时整图替换（不做 diff） */
export interface WorkflowSavePayload {
  name: string
  description?: string | null
  status?: WorkflowStatus
  nodes: WorkflowNodeData[]
  edges: WorkflowEdgeData[]
}

export interface WorkflowPageParams {
  page: number
  pageSize: number
  name?: string
  enabled?: boolean
}

export type WorkflowId = number | string

/* ------------------------------------------------------------------ */
/* API 方法                                                            */
/* ------------------------------------------------------------------ */

const BASE_URL = '/v1/workflows'

/** 分页查询工作流（行内含节点/连线数量，不含图） */
export function getWorkflowList(params: WorkflowPageParams) {
  const { page, pageSize, name, enabled } = params
  return get<PagedData<WorkflowListItem>>(BASE_URL, {
    params: {
      page,
      pageSize,
      ...(name !== undefined && name.trim() ? { name: name.trim() } : {}),
      ...(enabled !== undefined ? { status: enabled ? 1 : 0 } : {}),
    },
  })
}

/** 工作流详情：三表组装的完整节点和连线 */
export function getWorkflowDetail(id: WorkflowId) {
  return get<WorkflowDetail>(`${BASE_URL}/${id}`)
}

/** 下拉选项（与后端 WorkflowOptionDto 对齐，只取启用工作流） */
export interface WorkflowOption {
  id: number
  name: string
}

/** 启用工作流下拉选项（Agent 绑定用） */
export function getWorkflowOptions() {
  return get<WorkflowOption[]>(`${BASE_URL}/options`)
}

export function createWorkflow(data: WorkflowSavePayload) {
  return post<WorkflowDetail>(BASE_URL, data)
}

export function updateWorkflow(id: WorkflowId, data: WorkflowSavePayload) {
  return put<WorkflowDetail>(`${BASE_URL}/${id}`, data)
}

export function deleteWorkflow(id: WorkflowId) {
  return del<void>(`${BASE_URL}/${id}`)
}
