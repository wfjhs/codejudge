import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios';
import { useAuthStore } from '@/stores/auth';

// 开发时由 Vite proxy 转发到 8080，生产由 Nginx 同域转发，所以 baseURL 直接用相对路径。
const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

// 请求拦截：自动带 token
instance.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

// 响应拦截：统一解包 Result，非 0 码抛错给页面处理
instance.interceptors.response.use(
  (resp: AxiosResponse) => {
    const body = resp.data;
    if (body && typeof body.code === 'number') {
      if (body.code === 0) {
        return body.data;
      }
      // 401 / 1004 token 失效：清掉本地状态，跳登录
      if (body.code === 401 || body.code === 1004) {
        localStorage.removeItem('token');
        useAuthStore().logout();
        window.location.href = '/login';
      }
      return Promise.reject(new Error(body.message || '请求失败'));
    }
    return body;
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络错误';
    return Promise.reject(new Error(msg));
  },
);

// 包装一层：让 TS 类型反映"响应拦截已经解包成 T"，调用方直接拿到业务数据。
function wrap<T>(p: Promise<AxiosResponse<T>>): Promise<T> {
  return p as unknown as Promise<T>;
}

export function get<T>(url: string, config?: AxiosRequestConfig) {
  return wrap<T>(instance.get<T>(url, config));
}
export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return wrap<T>(instance.post<T>(url, data, config));
}
export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return wrap<T>(instance.put<T>(url, data, config));
}
export function del<T>(url: string, config?: AxiosRequestConfig) {
  return wrap<T>(instance.delete<T>(url, config));
}
