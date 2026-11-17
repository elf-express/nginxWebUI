import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadTail } from './api'

afterEach(() => vi.unstubAllGlobals())

describe('loadTail', () => {
  it('posts the saved id and viewer token as form data with the cancellation signal', async () => {
    // Arrange
    const signal = new AbortController().signal
    const data = { state: 'ready', path: '/var/log/nginx/access.log', lines: ['first', 'last'], limit: 500 }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true, status: 200, redirected: false,
      json: async () => ({ success: true, status: '200', obj: data }),
    })
    vi.stubGlobal('fetch', fetchMock)

    // Act
    const result = await loadTail('7 & 8', 'viewer+token', signal)

    // Assert
    expect(result).toEqual(data)
    expect(fetchMock.mock.calls[0][0]).toBe('/adminPage/log/tailData')
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'POST', signal, credentials: 'same-origin' })
    expect(fetchMock.mock.calls[0][1].body.toString()).toBe('id=7+%26+8&guid=viewer%2Btoken')
  })
})
