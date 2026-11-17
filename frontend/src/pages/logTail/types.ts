/** The complete bounded snapshot returned by the saved-log endpoint. */
export interface LogTailData {
  state: 'ready' | 'missing' | 'unreadable'
  path: string
  lines: string[]
  limit: 500
}
