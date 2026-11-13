export interface SseEvent {
  type: 'token' | 'done' | 'error'
  text?: string
  message?: string
  model?: string
  intent?: string
  latencyMs?: number
  logId?: string
}

export function parseSseEvents(body: string): SseEvent[] {
  const events: SseEvent[] = []
  const blocks = body.split('\n\n')
  for (const block of blocks) {
    const line = block.trim()
    if (!line.startsWith('data:')) continue
    const payload = line.slice(5).trim()
    if (!payload || payload === '[DONE]') continue
    try {
      events.push(JSON.parse(payload) as SseEvent)
    } catch {
      // skip malformed frames
    }
  }
  return events
}

export class SseStreamDecoder {
  private buffer = ''

  push(chunk: string, onEvent: (event: SseEvent) => void): void {
    this.buffer += chunk
    const blocks = this.buffer.split('\n\n')
    this.buffer = blocks.pop() ?? ''
    for (const block of blocks) {
      const event = this.parseBlock(block)
      if (event) onEvent(event)
    }
  }

  flush(onEvent: (event: SseEvent) => void): void {
    const event = this.parseBlock(this.buffer.trim())
    this.buffer = ''
    if (event) onEvent(event)
  }

  private parseBlock(block: string): SseEvent | null {
    const line = block.trim()
    if (!line.startsWith('data:')) return null
    const payload = line.slice(5).trim()
    if (!payload || payload === '[DONE]') return null
    try {
      return JSON.parse(payload) as SseEvent
    } catch {
      return null
    }
  }
}