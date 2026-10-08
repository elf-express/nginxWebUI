import { afterEach, expect, it, vi } from 'vitest'
import { ApiError, SessionExpiredError, get, post, setRedirect } from './http'

function fakeResponse(body: unknown, init: Partial<Response> = {}): Response {
  return {
    ok: true,
    status: 200,
    redirected: false,
    url: 'http://localhost/adminPage/basic/pageData',
    json: async () => body,
    ...init,
  } as Response
}

afterEach(() => {
  vi.unstubAllGlobals()
})

it('GET 把參數放進 query string 並回傳 obj', async () => {
  const fetchMock = vi.fn().mockResolvedValue(fakeResponse({ success: true, status: '200', obj: { a: 1 } }))
  vi.stubGlobal('fetch', fetchMock)

  await expect(get('/adminPage/basic/detail', { id: '7' })).resolves.toEqual({ a: 1 })
  expect(fetchMock.mock.calls[0][0]).toBe('/adminPage/basic/detail?id=7')
  expect(fetchMock.mock.calls[0][1].method).toBe('GET')
})

it('POST 以表單編碼送出', async () => {
  const fetchMock = vi.fn().mockResolvedValue(fakeResponse({ success: true, status: '200' }))
  vi.stubGlobal('fetch', fetchMock)

  await post('/adminPage/basic/del', { id: '1,2' })
  const init = fetchMock.mock.calls[0][1]
  expect(init.method).toBe('POST')
  expect(init.body).toBeInstanceOf(URLSearchParams)
  expect(init.body.toString()).toBe('id=1%2C2')
})

it('success=false 丟出帶後端訊息的 ApiError', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse({ success: false, status: '500', msg: '名稱重複' })))

  await expect(post('/adminPage/basic/addOver')).rejects.toEqual(new ApiError('名稱重複'))
})

it('HTTP 錯誤丟出空訊息的 ApiError', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse({}, { ok: false, status: 500 })))

  await expect(get('/adminPage/basic/pageData')).rejects.toEqual(new ApiError(''))
})

it('回應不是 JSON 丟出空訊息的 ApiError', async () => {
  const notJson = fakeResponse(null, { json: async () => { throw new SyntaxError('bad') } })
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(notJson))

  await expect(get('/adminPage/basic/pageData')).rejects.toEqual(new ApiError(''))
})

it('被導向登入頁時整頁跳轉並丟 SessionExpiredError', async () => {
  const redirect = vi.fn()
  setRedirect(redirect)
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse(null, { redirected: true, url: 'http://localhost/adminPage/login' })))

  await expect(get('/adminPage/basic/pageData')).rejects.toBeInstanceOf(SessionExpiredError)
  expect(redirect).toHaveBeenCalledWith('/adminPage/login')
})
