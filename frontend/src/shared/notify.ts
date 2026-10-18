import { message } from 'ant-design-vue'
import { ApiError, SessionExpiredError } from './http'
import { t } from './i18n'

export function notifyError(err: unknown): void {
  if (err instanceof SessionExpiredError) {
    return
  }
  console.error(err)
  const serverMsg = err instanceof ApiError ? err.message : ''
  message.error(serverMsg || t('commonStr.errorInfo'))
}
