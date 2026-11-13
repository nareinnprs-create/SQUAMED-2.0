import { describe, expect, it } from 'vitest'
import { renderMessage, targetRoute } from './command-bar'

describe('targetRoute', () => {
  it('maps known targets to page routes', () => {
    expect(targetRoute('laboratory')).toBe('/laboratory')
    expect(targetRoute('billing')).toBe('/billing')
    expect(targetRoute('integrations')).toBe('/integrations')
    expect(targetRoute('ai')).toBe('/ai')
    expect(targetRoute('patients')).toBe('/patients')
  })

  it('falls back to a slug for unknown targets', () => {
    expect(targetRoute('General Ward')).toBe('/general-ward')
  })

  it('handles null and empty targets', () => {
    expect(targetRoute(null)).toBe('')
    expect(targetRoute('')).toBe('')
  })

  it('is case insensitive', () => {
    expect(targetRoute('Pharmacy')).toBe('/pharmacy')
  })
})

describe('renderMessage', () => {
  it('splits ** markers into bold segments', () => {
    const result = renderMessage('There are **5 patients** on record.')
    expect(result).toHaveLength(3)
    const parts = divide(result as unknown[])
    expect(parts[0]).toBe('There are ')
    expect(parts[1]).toBe('5 patients')
    expect(parts[2]).toBe(' on record.')
  })

  it('returns a single span when there is no markdown', () => {
    expect(renderMessage('plain text')).toHaveLength(1)
  })
})

function divide(elements: unknown[]): string[] {
  return elements.map((element) => {
    const typed = element as { props?: { children?: string } }
    return typed.props?.children ?? ''
  })
}