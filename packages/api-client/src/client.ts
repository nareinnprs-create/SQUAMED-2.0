import type { AxiosInstance, AxiosRequestConfig } from 'axios';
import axios from 'axios';
import type { ApiResponse, ApiError, Paginated } from '@squamed/domain-types';

export interface ApiClientConfig {
  baseURL: string;
  getAccessToken: () => Promise<string | null>;
  onSessionExpired?: () => void;
}

export class ApiClient {
  private client: AxiosInstance;
  private getAccessToken: () => Promise<string | null>;
  private onSessionExpired?: () => void;

  constructor(config: ApiClientConfig) {
    this.getAccessToken = config.getAccessToken;
    this.onSessionExpired = config.onSessionExpired;

    this.client = axios.create({
      baseURL: config.baseURL,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    this.client.interceptors.request.use(async (cfg) => {
      const token = await this.getAccessToken();
      if (token) {
        cfg.headers.Authorization = `Bearer ${token}`;
      }
      return cfg;
    });

    this.client.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401) {
          this.onSessionExpired?.();
        }
        return Promise.reject(error);
      }
    );
  }

  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.get<ApiResponse<T>>(url, config);
    return response.data.data;
  }

  async getList<T>(url: string, config?: AxiosRequestConfig): Promise<Paginated<T>> {
    const response = await this.client.get<Paginated<T>>(url, config);
    return response.data;
  }

  async post<T, B = unknown>(url: string, body?: B, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.post<ApiResponse<T>>(url, body, config);
    return response.data.data;
  }

  async put<T, B = unknown>(url: string, body?: B, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.put<ApiResponse<T>>(url, body, config);
    return response.data.data;
  }

  async patch<T, B = unknown>(url: string, body?: B, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.patch<ApiResponse<T>>(url, body, config);
    return response.data.data;
  }

  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.delete<ApiResponse<T>>(url, config);
    return response.data.data;
  }

  extractError(error: unknown): ApiError['error'] {
    if (axios.isAxiosError(error)) {
      const data = error.response?.data as ApiError | undefined;
      if (data?.error) {
        return data.error;
      }
      return {
        code: 'network_error',
        message: error.message,
      };
    }
    return {
      code: 'unknown_error',
      message: 'An unknown error occurred',
    };
  }
}

export default ApiClient;