import type {
  AnalyzeRequest,
  CourseDetailResponse,
  ParserStatusResponse,
  TranslationProfilePayload,
  TaskResponse,
  TaskSummary
} from '../../types/media';
import { API_BASE, buildAbsoluteUrl, requestJson } from './http';

export async function fetchTasks() {
  const tasks = await requestJson<TaskSummary[]>('/api/parser/tasks');
  return [...tasks].sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
}

export function submitAnalyze(request: AnalyzeRequest) {
  return requestJson<TaskResponse>('/api/parser/analyze', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request)
  });
}

export function submitLocalSubtitleAnalyze(request: {
  title?: string;
  subtitleContent: string;
  subtitleFormat?: string;
  sourceLang?: string;
  targetLang?: string;
  translationProfile?: AnalyzeRequest['translationProfile'];
}) {
  return requestJson<TaskResponse>('/api/parser/local-subtitles', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request)
  });
}

export function submitCourseRetranslate(
  videoId: string,
  request: {
    sourceLang?: string;
    targetLang?: string;
    translationProfile?: AnalyzeRequest['translationProfile'];
  }
) {
  return requestJson<TaskResponse>(`/api/course/${videoId}/retranslate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request)
  });
}

export function fetchTaskStatus(taskId: string) {
  return requestJson<ParserStatusResponse>(`/api/parser/status/${taskId}`);
}

export async function fetchCourseDetail(videoId: string) {
  const detail = await requestJson<CourseDetailResponse>(`/api/course/${videoId}/detail`);
  return {
    ...detail,
    videoUrl: buildAbsoluteUrl(detail.videoUrl)
  };
}

export async function deleteTask(taskId: string) {
  const response = await fetch(`${API_BASE}/api/parser/tasks/${taskId}`, { method: 'DELETE' });
  if (!response.ok) {
    throw new Error('删除任务失败');
  }
}

export async function clearFailedTasks() {
  const response = await fetch(`${API_BASE}/api/parser/tasks/failed`, { method: 'DELETE' });
  if (!response.ok) {
    throw new Error('清理失败任务失败');
  }
}

export function buildDownloadUrl(videoId: string) {
  return `${API_BASE}/api/course/${videoId}/download`;
}

export function testTranslationProfile(request: TranslationProfilePayload & { sourceLang?: string; targetLang?: string }) {
  return requestJson<{
    ok: boolean;
    message: string;
    translatedText: string;
    elapsedMs: number;
  }>('/api/translation/test', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request)
  });
}
