import { ElMessageBox } from 'element-plus'

import { notifySuccess } from './notify'

export interface UseConfirmOptions {
  title?: string
  /** 确认正文，告知操作后果 */
  text?: string
  confirmText?: string
  cancelText?: string
  /** 成功后的通知文案 */
  successText?: string
}

/**
 * 用户在确认框点击「取消」时的返回值。
 * 必须与成功结果区分：删除等 void 接口成功时 api 返回的也是 null/undefined，
 * 不能用 `result === null` 判断是否取消。
 */
export const CONFIRM_CANCELLED = Symbol('confirm-cancelled')

/**
 * 删除（或其他危险操作）确认 composable。
 *
 * 一行声明：
 *   const removeProvider = useConfirm(deleteProvider, { successText: '删除成功' })
 * 一行使用：
 *   const result = await removeProvider(row.id)
 *   if (result !== CONFIRM_CANCELLED) refresh()
 *
 * 流程：弹确认框 → 调 api(...args) → 成功通知。
 * 用户取消返回 {@link CONFIRM_CANCELLED}；api 抛出（失败）时错误继续向上传播。
 */
export function useConfirm<A extends unknown[], R>(
  api: (...args: A) => Promise<R>,
  options: UseConfirmOptions = {},
) {
  const {
    title = '操作确认',
    text = '此操作不可恢复，确定继续吗？',
    confirmText = '确定',
    cancelText = '取消',
    successText = '操作成功',
  } = options

  return async (...args: A): Promise<R | typeof CONFIRM_CANCELLED> => {
    try {
      await ElMessageBox.confirm(text, title, {
        confirmButtonText: confirmText,
        cancelButtonText: cancelText,
        type: 'warning',
      })
    } catch {
      // 用户点击取消，视为放弃，不提示
      return CONFIRM_CANCELLED
    }

    const result = await api(...args)
    notifySuccess(successText)
    return result
  }
}
