const getApiBase = () => import.meta.env.VITE_API_BASE ?? '';

export const API_BASE = getApiBase();

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init);
  if (!response.ok) {
    const text = await response.text();
    let parsedMessage = '';
    try {
      const payload = JSON.parse(text) as { message?: string; error?: string };
      parsedMessage = payload.message || payload.error || '';
    } catch {
      parsedMessage = '';
    }
    const readableText = parsedMessage || text.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
    throw new Error(readableText || `请求失败：${response.status}`);
  }
  return response.json() as Promise<T>;
}

export function buildAbsoluteUrl(path: string) {
  if (!path.startsWith('/')) {
    return path;
  }

  return `${API_BASE}${path}`;
}
