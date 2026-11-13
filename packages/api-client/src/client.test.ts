import { describe, expect, it } from 'vitest';
import ApiClient from './client';

function makeClient() {
  return new ApiClient({
    baseURL: 'https://api.example.test',
    getAccessToken: async () => 'token-123',
  });
}

describe('ApiClient.extractError', () => {
  it('returns a structured error for axios failures', () => {
    const client = makeClient();
    const axiosError = {
      isAxiosError: true,
      response: {
        status: 401,
        data: { error: { code: 'unauthorized', message: 'Session expired' } },
      },
    };
    const result = client.extractError(axiosError);
    expect(result.code).toBe('unauthorized');
    expect(result.message).toBe('Session expired');
  });

  it('falls back to a network error when no payload exists', () => {
    const client = makeClient();
    const networkError = { isAxiosError: true, message: 'Network Error', response: undefined };
    const result = client.extractError(networkError);
    expect(result.code).toBe('network_error');
    expect(result.message).toBe('Network Error');
  });

  it('handles non-axios errors', () => {
    const client = makeClient();
    const result = client.extractError(new Error('boom'));
    expect(result.code).toBe('unknown_error');
    expect(result.message).toBe('An unknown error occurred');
  });
});