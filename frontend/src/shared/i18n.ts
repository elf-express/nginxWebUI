import { get } from './http'

export type Messages = Record<string, string>

let messages: Messages = {}

export function setMessages(next: Messages): void {
  messages = next
}

// 只做 {0}、{1} 位置替換；properties 值裡的 @ | { } 都是字面值
export function t(key: string, ...args: (string | number)[]): string {
  const template = messages[key]
  if (template === undefined) {
    return key
  }
  return template.replace(/\{(\d+)\}/g, (whole, index: string) => {
    const arg = args[Number(index)]
    return arg === undefined ? whole : String(arg)
  })
}

export function te(key: string): boolean {
  return key in messages
}

export async function loadMessages(): Promise<string> {
  const data = await get<{ lang: string; messages: Messages }>('/adminPage/i18n')
  setMessages(data.messages)
  return data.lang
}
