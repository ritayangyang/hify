import { get } from '@/utils/request'

/** 与后端 ModelOptionDto 对齐：模型下拉选项，附带提供商名称 */
export interface ModelOption {
  id: number
  name: string
  modelId: string
  /** 1 对话模型，2 向量模型 */
  modelType: 1 | 2
  status: 0 | 1
  providerId: number
  providerName: string
}

/** 已启用的对话模型选项（Agent 绑定模型下拉） */
export function getChatModelOptions() {
  return get<ModelOption[]>('/v1/model-configs/chat-options')
}
