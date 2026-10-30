import { del, get, post, put } from '@/utils/request'
import type { PagedData } from '@/components/types'

/* ------------------------------------------------------------------ */
/* 类型定义（与后端 com.hify.modules.agent.api.dto 对齐）               */
/* ------------------------------------------------------------------ */

/** 启用状态：1 启用，0 禁用 */
export type AgentStatus = 0 | 1

/** 生成参数覆盖项（均可选，空值不下发） */
export interface AgentModelParams {
  temperature?: number
  topP?: number
  maxTokens?: number
  [key: string]: number | undefined
}

/** 与后端 AgentDto 对齐 */
export interface AgentVO {
  id: number
  name: string
  description: string | null
  systemPrompt: string | null
  modelConfigId: number
  /** 绑定工作流 id；null 表示普通对话 */
  workflowId: number | null
  modelParams: AgentModelParams | null
  status: AgentStatus
  /** ISO-8601 时间字符串 */
  createdAt: string
  updatedAt: string
}

/** 与后端 AgentListItemDto 对齐：列表行 = Agent 字段 + 模型摘要 + 关联数 */
export interface AgentListItem extends AgentVO {
  /** 绑定模型展示名；模型被物理删除时为 null */
  modelName: string | null
  providerName: string | null
  knowledgeCount: number
  mcpCount: number
}

/** 与后端 AgentDetailDto 对齐 */
export interface AgentDetail {
  agent: AgentVO
  modelName: string | null
  providerName: string | null
  knowledgeIds: number[]
  mcpToolIds: number[]
}

/** 创建入参 */
export interface AgentCreatePayload {
  name: string
  description?: string
  systemPrompt?: string
  modelConfigId: number
  /** 绑定工作流；表单始终显式提交，null 表示普通对话/解绑 */
  workflowId?: number | null
  modelParams?: AgentModelParams
  status?: AgentStatus
  knowledgeIds?: number[]
  mcpToolIds?: number[]
}

/** 更新入参：编辑表单整体提交，关联按数组整体替换 */
export type AgentUpdatePayload = AgentCreatePayload

export interface AgentPageParams {
  page: number
  pageSize: number
  name?: string
  enabled?: boolean
}

export type AgentId = number | string

/* ------------------------------------------------------------------ */
/* API 方法                                                            */
/* ------------------------------------------------------------------ */

const BASE_URL = '/v1/agents'

/** 分页查询 Agent（行内含绑定模型摘要与知识库/MCP 关联数） */
export function getAgentList(params: AgentPageParams) {
  const { page, pageSize, name, enabled } = params
  return get<PagedData<AgentListItem>>(BASE_URL, {
    params: {
      page,
      pageSize,
      ...(name !== undefined && name.trim() ? { name: name.trim() } : {}),
      ...(enabled !== undefined ? { status: enabled ? 1 : 0 } : {}),
    },
  })
}

/** Agent 详情（含关联资源 id，编辑回填用） */
export function getAgentDetail(id: AgentId) {
  return get<AgentDetail>(`${BASE_URL}/${id}`)
}

export function createAgent(data: AgentCreatePayload) {
  return post<AgentVO>(BASE_URL, data)
}

export function updateAgent(id: AgentId, data: AgentUpdatePayload) {
  return put<AgentVO>(`${BASE_URL}/${id}`, data)
}

export function deleteAgent(id: AgentId) {
  return del<void>(`${BASE_URL}/${id}`)
}
