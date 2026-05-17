export type TaskState = 'pending' | 'processing' | 'completed' | 'failed';
export type AppearanceMode = 'system' | 'light' | 'dark';
export type SubtitleMode = 'dual' | 'source' | 'target' | 'hidden';
export type TranslationProvider =
  | 'free'
  | 'openai-compatible'
  | 'openai'
  | 'deepseek'
  | 'doubao'
  | 'siliconflow'
  | 'zhipu'
  | 'qwen'
  | 'gemini'
  | 'claude'
  | 'custom';
export type TranslationServiceTier = 'free' | 'paid' | 'custom';

export interface TaskSummary {
  taskId: string;
  status: TaskState;
  progress: number;
  videoId?: string | null;
  url: string;
  title: string;
  createdAt: number;
}

export interface AnalyzeRequest {
  url: string;
  sourceLang?: string;
  targetLang?: string;
  translationProfile?: {
    provider: TranslationProvider;
    baseUrl: string;
    model: string;
    apiKey: string;
    prompt?: string;
  };
}

export interface TaskResponse {
  taskId: string;
  status: TaskState;
  message: string;
}

export interface ParserStatusResponse {
  status: TaskState;
  progress: number;
  videoId?: string | null;
  error?: string | null;
  title?: string | null;
  sourceLang?: string | null;
  targetLang?: string | null;
}

export interface BackendSubtitle {
  startTime: number;
  endTime: number;
  en?: string;
  cn?: string;
  sourceText?: string;
  targetText?: string;
  sourceLang?: string;
  targetLang?: string;
}

export interface DisplaySubtitle {
  id: string;
  startTime: number;
  endTime: number;
  sourceText: string;
  targetText: string;
  sourceLang?: string;
  targetLang?: string;
}

export interface CourseDetailResponse {
  title: string;
  videoUrl: string;
  subtitles: BackendSubtitle[];
  sourceLang?: string;
  targetLang?: string;
  hasVideo?: boolean;
}

export interface ImportedMediaRecord {
  id: string;
  title: string;
  videoName: string;
  subtitleName?: string;
  videoFile: Blob;
  subtitleFile?: Blob;
  subtitleTaskId?: string;
  createdAt: number;
}

export interface ImportedMediaSummary {
  id: string;
  title: string;
  videoName: string;
  subtitleName?: string;
  subtitleTaskId?: string;
  createdAt: number;
}

export interface TranslationProfile {
  id: string;
  name: string;
  provider: TranslationProvider;
  tier?: TranslationServiceTier;
  baseUrl: string;
  model: string;
  apiKey: string;
  prompt?: string;
  requiresApiKey?: boolean;
  enabled: boolean;
  isDefault: boolean;
  createdAt: number;
}

export interface StoredTranslationProfile {
  id: string;
  name: string;
  provider: TranslationProvider;
  tier?: TranslationServiceTier;
  baseUrl: string;
  model: string;
  encryptedApiKey: string;
  prompt?: string;
  requiresApiKey?: boolean;
  enabled?: boolean;
  isDefault?: boolean;
  createdAt: number;
}

export interface TranslationProfilePayload {
  provider: TranslationProvider;
  baseUrl: string;
  model: string;
  apiKey: string;
  prompt?: string;
}
