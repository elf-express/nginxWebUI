import { post } from '../../shared/http'
import type { LogTailData } from './types'

export function loadTail(id: string, guid: string, signal: AbortSignal): Promise<LogTailData> {
  return post<LogTailData>('/adminPage/log/tailData', { id, guid }, { signal })
}
