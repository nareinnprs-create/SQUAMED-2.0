import { describe, expect, it } from 'vitest'
import { SseStreamDecoder, parseSseEvents } from './sse'

describe('parseSseEvents', () => {
  it('extracts token and done events from an SSE body', () => {
    const body = [
      'data: {"type":"token","text":"Open to "}',
      '',
      'data: {"type":"token","text":"laboratory"}',
      '',
      'data: {"type":"done","model":"SQUAMED Local Assistant","intent":"navigate","latencyMs":42,"logId":"abc"}',
      '',
      '',
    ].join('\n')
    const events = parseSseEvents(body)
    expect(events).toHaveLength(3)
    expect(events[0]).toMatchObject({ type: 'token', text: 'Open to ' })
    expect(events[2]).toMatchObject({ type: 'done', model: 'SQUAMED Local Assistant', latencyMs: 42 })
  })

  it('skips malformed payloads and [DONE] markers', () => {
    const events = parseSseEvents('data: not-json\n\ndata: [DONE]\n\ndata: {"type":"error","message":"boom"}\n\n')
    expect(events).toHaveLength(1)
    expect(events[0]).toMatchObject({ type: 'error', message: 'boom' })
  })

  it('handles an empty body', () => {
    expect(parseSseEvents('')).toEqual([])
  })
})

describe('SseStreamDecoder', () => {
  it('buffers partial frames across chunks', () => {
    const decoder = new SseStreamDecoder()
    const events: string[] = []
    decoder.push('data: {"type":"token","text":"hel', (event) => events.push(event.text ?? ''))
    expect(events).toHaveLength(0)
    decoder.push('lo"}\n\n', (event) => events.push(event.text ?? ''))
    expect(events).toEqual(['hello'])
  })

  it('handles multiple frames in one chunk', () => {
    const decoder = new SseStreamDecoder()
    const types: string[] = []
    const body = 'data: {"type":"token","text":"a"}\n\ndata: {"type":"done","intent":"count"}\n\n'
    decoder.push(body, (event) => types.push(event.type))
    decoder.flush(() => {})
    expect(types).toEqual(['token', 'done'])
  })
})