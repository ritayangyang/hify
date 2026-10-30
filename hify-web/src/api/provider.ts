import { del, get, post, put } from '@/utils/request'
import type { PagedData } from '@/components/types'

/* ------------------------------------------------------------------ */
/* 类型定义（与后端 com.hify.modules.model.api.dto 对齐）               */
/* ------------------------------------------------------------------ */

/** ProviderType 数字编码（出参一律为数字，库内存 TINYINT） */
export type ProviderTypeCode = 1 | 2 | 3 | 4

/** ProviderType 枚举名（入参可传字符串名） */
export type ProviderTypeName = 'OPENAI' | 'OPENAI_COMPATIBLE' | 'ANTHROPIC' | 'OLLAMA'

/** 入参：数字编码或枚举名均可；出参见 ProviderVO.type */
export type ProviderType = ProviderTypeCode | ProviderTypeName

/** 模型类型：1 对话模型，2 向量模型 */
export type ModelType = 1 | 2

/** 启用状态：1 启用，0 禁用 */
export type ProviderStatus = 0 | 1

/** 认证配置；出参中 apiKey 恒为 "******"，无认证类型为 {} */
export interface ProviderAuthConfig {
  apiKey?: string
  [key: string]: unknown
}

/** 与后端 ProviderDto 对齐 */
export interface ProviderVO {
  id: number
  code: string
  type: ProviderTypeCode
  name: string
  baseUrl: string
  authConfig: ProviderAuthConfig
  status: ProviderStatus
  /** ISO-8601 时间字符串 */
  createdAt: string
  updatedAt: string
}

/** 与后端 ModelConfigDto 对齐：展示名 name 与真实模型标识 modelId 分开 */
export interface ModelConfigVO {
  id: number
  providerId: number
  name: string
  modelId: string
  modelType: ModelType
  status: ProviderStatus
  extraParams: Record<string, unknown> | null
}

/** 与后端 ProviderHealthDto 对齐 */
export interface ProviderHealthVO {
  providerId: number
  failCount: number
  latencyMs: number | null
  lastSuccessAt: string | null
}

/** 与后端 ProviderDetailDto 对齐 */
export interface ProviderDetailVO {
  provider: ProviderVO
  modelConfigs: ModelConfigVO[]
  health: ProviderHealthVO
}

/**
 * 健康状态（后端由连通性测试历史派生）：
 * UP 最近测试成功；DEGRADED 曾成功但最近连续失败；
 * DOWN 从未成功且有失败；UNKNOWN 从未测试
 */
export type ProviderHealthStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN'

/** 与后端 ProviderListItemDto 对齐：列表行 = Provider 字段 + 健康摘要 + 已启用模型数 */
export interface ProviderListItem extends ProviderVO {
  failCount: number
  latencyMs: number | null
  lastSuccessAt: string | null
  healthStatus: ProviderHealthStatus
  /** 该提供商下已启用（status=1）的模型数量 */
  enabledModelCount: number
}

/** 与后端 ConnectionTestResult 对齐；无论成功失败 HTTP 均为 200 */
export interface ConnectionTestResult {
  success: boolean
  latencyMs: number
  modelCount: number
  errorMessage: string | null
}

/* ------------------------------------------------------------------ */
/* 请求入参                                                            */
/* ------------------------------------------------------------------ */

/** 分页查询入参；enabled 在请求层转换为后端的 status（true→1 / false→0） */
export interface ProviderPageParams {
  page: number
  pageSize: number
  /** 按类型筛选，枚举名或数字编码 */
  type?: ProviderType
  /** 按启用状态筛选；不传表示不筛选 */
  enabled?: boolean
}

/** 创建入参；code 留空时后端按 type 自动生成 */
export interface ProviderCreatePayload {
  code?: string
  type: ProviderType
  name: string
  baseUrl: string
  /** OLLAMA 等无认证类型可不传 */
  authConfig?: ProviderAuthConfig
  status?: ProviderStatus
}

/** 更新入参：全部可选，null/不传的字段保留原值；code 不可改 */
export interface ProviderUpdatePayload {
  type?: ProviderType
  name?: string
  baseUrl?: string
  authConfig?: ProviderAuthConfig
  status?: ProviderStatus
}

export type ProviderId = number | string

/* ------------------------------------------------------------------ */
/* API 方法                                                            */
/* ------------------------------------------------------------------ */

const BASE_URL = '/v1/providers'

/** 分页查询提供商列表（行内含健康状态摘要与已启用模型数） */
export function getProviderList(params: ProviderPageParams) {
  const { page, pageSize, type, enabled } = params
  return get<PagedData<ProviderListItem>>(BASE_URL, {
    params: {
      page,
      pageSize,
      ...(type !== undefined ? { type } : {}),
      ...(enabled !== undefined ? { status: enabled ? 1 : 0 } : {}),
    },
  })
}

/** 创建提供商，返回脱敏后的提供商信息 */
export function createProvider(data: ProviderCreatePayload) {
  return post<ProviderVO>(BASE_URL, data)
}

/** 更新提供商（部分更新） */
export function updateProvider(id: ProviderId, data: ProviderUpdatePayload) {
  return put<ProviderVO>(`${BASE_URL}/${id}`, data)
}

/** 删除提供商（逻辑删除，级联模型配置与健康记录） */
export function deleteProvider(id: ProviderId) {
  return del<void>(`${BASE_URL}/${id}`)
}

/** 对已保存的提供商做连通性测试，服务端会回写健康状态 */
export function testConnection(id: ProviderId) {
  return post<ConnectionTestResult>(`${BASE_URL}/${id}/test-connection`)
}
