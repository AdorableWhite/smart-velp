import type { TranslationProvider, TranslationServiceTier } from '../../types/media';

export interface TranslationServicePreset {
  id: string;
  name: string;
  provider: TranslationProvider;
  tier: TranslationServiceTier;
  baseUrl: string;
  model: string;
  requiresApiKey: boolean;
  description: string;
  badge: string;
  defaultPrompt: string;
}

export const defaultTranslationPrompt =
  '你是专业字幕翻译。请把字幕从 {{sourceLang}} 翻译成 {{targetLang}}，保持原意、语气和句子数量。只输出 JSON 字符串数组，不要解释，不要 Markdown。';

export const translationServiceCatalog: TranslationServicePreset[] = [
  {
    id: 'free-built-in',
    name: '免填 Key（服务端额度）',
    provider: 'free',
    tier: 'free',
    baseUrl: '',
    model: 'built-in-free',
    requiresApiKey: false,
    badge: '免填 Key',
    description: '不需要用户填写 API Key，但依赖服务端配置和额度；是否可用以连通性检测为准。',
    defaultPrompt: defaultTranslationPrompt
  },
  {
    id: 'siliconflow-qwen',
    name: '硅基流动 Qwen',
    provider: 'siliconflow',
    tier: 'paid',
    baseUrl: 'https://api.siliconflow.cn/v1',
    model: 'Qwen/Qwen2.5-7B-Instruct',
    requiresApiKey: true,
    badge: 'API Key',
    description: 'OpenAI 兼容接口，适合接入硅基流动上的 Qwen、DeepSeek 等模型。',
    defaultPrompt: defaultTranslationPrompt
  },
  {
    id: 'deepseek-chat',
    name: 'DeepSeek',
    provider: 'deepseek',
    tier: 'paid',
    baseUrl: 'https://api.deepseek.com/chat/completions',
    model: 'deepseek-chat',
    requiresApiKey: true,
    badge: '付费',
    description: 'DeepSeek 官方 Chat Completions 接口。',
    defaultPrompt: defaultTranslationPrompt
  },
  {
    id: 'doubao-ark',
    name: '豆包 / 火山方舟',
    provider: 'doubao',
    tier: 'paid',
    baseUrl: 'https://ark.cn-beijing.volces.com/api/v3/responses',
    model: 'doubao-seed-1-6-251015',
    requiresApiKey: true,
    badge: '付费',
    description: '火山方舟 Responses 接口，适合豆包系列模型。',
    defaultPrompt: defaultTranslationPrompt
  },
  {
    id: 'openai',
    name: 'OpenAI',
    provider: 'openai',
    tier: 'paid',
    baseUrl: 'https://api.openai.com/v1',
    model: 'gpt-4o-mini',
    requiresApiKey: true,
    badge: '付费',
    description: 'OpenAI 兼容 Chat Completions 接口。',
    defaultPrompt: defaultTranslationPrompt
  },
  {
    id: 'zhipu',
    name: '智谱 GLM',
    provider: 'zhipu',
    tier: 'paid',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    model: 'glm-4-flash',
    requiresApiKey: true,
    badge: 'API Key',
    description: '智谱 GLM OpenAI 兼容接口。',
    defaultPrompt: defaultTranslationPrompt
  },
  {
    id: 'qwen',
    name: '通义千问',
    provider: 'qwen',
    tier: 'paid',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    model: 'qwen-plus',
    requiresApiKey: true,
    badge: 'API Key',
    description: 'DashScope OpenAI 兼容模式。',
    defaultPrompt: defaultTranslationPrompt
  },
  {
    id: 'gemini',
    name: 'Gemini',
    provider: 'gemini',
    tier: 'paid',
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
    model: 'gemini-2.5-flash',
    requiresApiKey: true,
    badge: 'API Key',
    description: 'Gemini OpenAI 兼容入口。',
    defaultPrompt: defaultTranslationPrompt
  },
  {
    id: 'custom-openai',
    name: '自定义 OpenAI 兼容',
    provider: 'openai-compatible',
    tier: 'custom',
    baseUrl: '',
    model: '',
    requiresApiKey: true,
    badge: '自定义',
    description: '填入自己的 Base URL、模型和 API Key，适合私有模型或聚合服务。',
    defaultPrompt: defaultTranslationPrompt
  }
];

export function providerDisplayName(provider: TranslationProvider) {
  return translationServiceCatalog.find((service) => service.provider === provider)?.name ?? provider;
}
