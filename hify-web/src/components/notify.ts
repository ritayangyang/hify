import { ElNotification } from 'element-plus'

/** 统一通知封装：固定位置、时长与宽度，全站风格一致 */

const DEFAULT_DURATION = 3000

export function notifySuccess(message: string, title = '成功') {
  ElNotification({
    title,
    message,
    type: 'success',
    duration: DEFAULT_DURATION,
    position: 'top-right',
    customClass: 'hify-notification',
  })
}

export function notifyError(message: string, title = '错误') {
  ElNotification({
    title,
    message,
    type: 'error',
    duration: DEFAULT_DURATION,
    position: 'top-right',
    customClass: 'hify-notification',
  })
}

export function notifyWarning(message: string, title = '警告') {
  ElNotification({
    title,
    message,
    type: 'warning',
    duration: DEFAULT_DURATION,
    position: 'top-right',
    customClass: 'hify-notification',
  })
}
