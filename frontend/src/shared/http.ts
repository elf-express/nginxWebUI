export interface JsonResult<T> {
  success: boolean
  status: string
  msg?: string
  obj?: T
}

/** 後端回 success=false 時 message 是後端訊息；HTTP 錯誤或回應無法解析時 message 為空字串 */
export class ApiError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message)
  }
}

export class SessionExpiredError extends Error {}

export type Params = Record<string, string | number>
export interface RequestOptions {
  signal?: AbortSignal
}

// 一律用同源相對路徑：遠端模式下 AppFilter 會把 /adminPage/** 轉發給遠端，前端不必區分本機或遠端
const LOGIN_PATH = '/adminPage/login'

let redirect = (url: string): void => {
  window.location.href = url
}

export function setRedirect(fn: (url: string) => void): void {
  redirect = fn
}

async function request<T>(method: 'GET' | 'POST', url: string, params: Params = {}, options?: RequestOptions): Promise<T> {
  const form = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    form.append(key, String(value))
  }

  const init: RequestInit = { method, credentials: 'same-origin', headers: { Accept: 'application/json' } }
  if (options?.signal) init.signal = options.signal
  let target = url
  if (method === 'GET') {
    const query = form.toString()
    if (query) {
      target += `?${query}`
    }
  } else {
    init.body = form
  }

  const res = await fetch(target, init)

  // 未登入時 AppFilter 回 302 到登入頁，fetch 會跟著轉址
  // 遠端節點連不上時 AppFilter 轉到 /adminPage/login/noServer，原樣跳過去而不是回登入頁
  const finalPath = res.redirected ? new URL(res.url, window.location.href).pathname : ''
  if (finalPath.startsWith(LOGIN_PATH)) {
    redirect(finalPath)
    throw new SessionExpiredError(res.url)
  }
  if (!res.ok) {
    throw new ApiError('', res.status)
  }

  let data: JsonResult<T>
  try {
    data = (await res.json()) as JsonResult<T>
  } catch (err) {
    if (typeof err === 'object' && err !== null && 'name' in err && err.name === 'AbortError') throw err
    throw new ApiError('')
  }
  if (!data.success) {
    throw new ApiError(data.msg ?? '')
  }
  return data.obj as T
}

export function get<T>(url: string, params?: Params, options?: RequestOptions): Promise<T> {
  return request<T>('GET', url, params, options)
}

export function post<T = void>(url: string, params?: Params, options?: RequestOptions): Promise<T> {
  return request<T>('POST', url, params, options)
}
