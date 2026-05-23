import { type FormEvent, useEffect, useMemo, useState } from 'react';
import { fetchTranslationHealth, testTranslationProfile } from '../../services/api/media';
import {
  deleteTranslationProfile,
  listTranslationProfiles,
  saveTranslationProfile,
  setDefaultTranslationProfile,
  updateTranslationProfileState
} from '../../services/storage/translationProfiles';
import {
  defaultTranslationPrompt,
  translationServiceCatalog,
  type TranslationServicePreset
} from '../../services/storage/translationServiceCatalog';
import { usePreferencesStore } from '../../store/usePreferencesStore';
import type {
  TranslationProfile,
  TranslationProvider,
  TranslationProviderHealth,
  TranslationServiceTier
} from '../../types/media';

type TestState = Record<
  string,
  { status: 'testing' | 'ok' | 'failed'; message: string; elapsedMs?: number; providerChain?: string[] }
>;

type ServiceDraft = {
  id?: string;
  name: string;
  provider: TranslationProvider;
  tier: TranslationServiceTier;
  baseUrl: string;
  model: string;
  apiKey: string;
  prompt: string;
  requiresApiKey: boolean;
  enabled: boolean;
  description?: string;
};

const tierLabels: Record<TranslationServiceTier, string> = {
  free: '免填 Key / 服务端额度',
  paid: '在线 API 服务',
  custom: '自定义服务'
};

const providerLabels: Partial<Record<TranslationProvider, string>> = {
  free: '免填 Key',
  'openai-compatible': 'OpenAI 兼容',
  openai: 'OpenAI',
  siliconflow: '硅基流动',
  deepseek: 'DeepSeek',
  doubao: '豆包',
  zhipu: '智谱',
  qwen: '通义千问',
  gemini: 'Gemini',
  claude: 'Claude',
  custom: '自定义'
};

function serviceKey(provider: TranslationProvider, baseUrl: string, model: string) {
  return `${provider}:${baseUrl}:${model}`;
}

function profileKey(profile: TranslationProfile) {
  return serviceKey(profile.provider, profile.baseUrl, profile.model);
}

function draftFromPreset(preset: TranslationServicePreset): ServiceDraft {
  return {
    name: preset.name,
    provider: preset.provider,
    tier: preset.tier,
    baseUrl: preset.baseUrl,
    model: preset.model,
    apiKey: '',
    prompt: preset.defaultPrompt,
    requiresApiKey: preset.requiresApiKey,
    enabled: true,
    description: preset.description
  };
}

function draftFromProfile(profile: TranslationProfile): ServiceDraft {
  const preset = translationServiceCatalog.find((service) => serviceKey(service.provider, service.baseUrl, service.model) === profileKey(profile));
  return {
    id: profile.id,
    name: profile.name,
    provider: profile.provider,
    tier: profile.tier ?? preset?.tier ?? 'custom',
    baseUrl: profile.baseUrl,
    model: profile.model,
    apiKey: profile.apiKey,
    prompt: profile.prompt ?? preset?.defaultPrompt ?? defaultTranslationPrompt,
    requiresApiKey: profile.requiresApiKey ?? preset?.requiresApiKey ?? true,
    enabled: profile.enabled,
    description: preset?.description
  };
}

function draftPayload(draft: ServiceDraft) {
  return {
    provider: draft.provider,
    baseUrl: draft.baseUrl,
    model: draft.model,
    apiKey: draft.requiresApiKey ? draft.apiKey : '',
    prompt: draft.prompt
  };
}

function statusLabel(profile?: TranslationProfile, fallback?: string) {
  if (!profile) {
    return fallback ?? '未保存';
  }
  if (profile.isDefault) {
    return '优先';
  }
  return profile.enabled ? '已启用' : '已停用';
}

export function ProfileManager() {
  const [profiles, setProfiles] = useState<TranslationProfile[]>([]);
  const [activeId, setActiveId] = useState<string>();
  const [draft, setDraft] = useState<ServiceDraft>(() => draftFromPreset(translationServiceCatalog[0]));
  const [loading, setLoading] = useState(true);
  const [testState, setTestState] = useState<TestState>({});
  const [providerHealth, setProviderHealth] = useState<Record<string, TranslationProviderHealth>>({});
  const sourceLang = usePreferencesStore((state) => state.sourceLang);
  const targetLang = usePreferencesStore((state) => state.targetLang);
  const selectedProfileId = usePreferencesStore((state) => state.selectedProfileId);
  const setSelectedProfileId = usePreferencesStore((state) => state.setSelectedProfileId);

  const reload = async () => {
    setLoading(true);
    const rows = await listTranslationProfiles();
    setProfiles(rows);
    setActiveId((current) => current ?? rows.find((profile) => profile.isDefault)?.id ?? rows[0]?.id);
    setLoading(false);
  };

  useEffect(() => {
    void reload();
    void fetchTranslationHealth().then((health) => setProviderHealth(health ?? {})).catch(() => setProviderHealth({}));
  }, []);

  const activeProfile = useMemo(
    () => profiles.find((profile) => profile.id === activeId),
    [activeId, profiles]
  );

  useEffect(() => {
    if (activeProfile) {
      setDraft(draftFromProfile(activeProfile));
    }
  }, [activeProfile]);

  const savedByService = useMemo(() => {
    const map = new Map<string, TranslationProfile>();
    profiles.forEach((profile) => map.set(profileKey(profile), profile));
    return map;
  }, [profiles]);

  const catalogKeys = useMemo(
    () => new Set(translationServiceCatalog.map((service) => serviceKey(service.provider, service.baseUrl, service.model))),
    []
  );

  const extraSavedProfiles = useMemo(
    () => profiles.filter((profile) => !catalogKeys.has(profileKey(profile))),
    [catalogKeys, profiles]
  );

  const groupedCatalog = useMemo(
    () =>
      translationServiceCatalog.reduce<Record<TranslationServiceTier, TranslationServicePreset[]>>(
        (groups, service) => {
          groups[service.tier].push(service);
          return groups;
        },
        { free: [], paid: [], custom: [] }
      ),
    []
  );

  const testKey = draft.id ?? 'draft';
  const currentTestState = testState[testKey];
  const providerLabel = providerLabels[draft.provider] ?? draft.provider;
  const currentProviderHealth = providerHealth[draft.provider];
  const canEditEndpoint = draft.provider !== 'free';
  const canEditApiKey = draft.requiresApiKey && draft.provider !== 'free';
  const healthSummary = currentProviderHealth
    ? currentProviderHealth.circuitOpen
      ? '当前处于冷却中'
      : currentProviderHealth.consecutiveFailures > 0
        ? `连续失败 ${currentProviderHealth.consecutiveFailures} 次`
        : '近期可用'
    : '尚未检测';

  const onSelectPreset = (preset: TranslationServicePreset) => {
    const saved = savedByService.get(serviceKey(preset.provider, preset.baseUrl, preset.model));
    if (saved) {
      setActiveId(saved.id);
      setDraft(draftFromProfile(saved));
      return;
    }

    setActiveId(undefined);
    setDraft(draftFromPreset(preset));
  };

  const onSelectProfile = (profile: TranslationProfile) => {
    setActiveId(profile.id);
    setDraft(draftFromProfile(profile));
  };

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const id = await saveTranslationProfile({
      id: draft.id,
      name: draft.name,
      provider: draft.provider,
      tier: draft.tier,
      baseUrl: draft.baseUrl,
      model: draft.model,
      apiKey: draft.requiresApiKey ? draft.apiKey : '',
      prompt: draft.prompt,
      requiresApiKey: draft.requiresApiKey,
      enabled: draft.enabled
    });
    setActiveId(id);
    await reload();
  };

  const onDelete = async (profileId: string) => {
    await deleteTranslationProfile(profileId);
    if (selectedProfileId === profileId) {
      setSelectedProfileId(undefined);
    }
    setActiveId(undefined);
    setDraft(draftFromPreset(translationServiceCatalog[0]));
    await reload();
  };

  const onSetDefault = async (profileId: string) => {
    await setDefaultTranslationProfile(profileId);
    setSelectedProfileId(profileId);
    await reload();
  };

  const onToggleEnabled = async (profile: TranslationProfile) => {
    await updateTranslationProfileState(profile.id, { enabled: !profile.enabled });
    setDraft((current) => ({ ...current, enabled: !profile.enabled }));
    await reload();
  };

  const onTestDraft = async () => {
    if (canEditApiKey && !draft.apiKey.trim()) {
      setTestState((current) => ({
        ...current,
        [testKey]: { status: 'failed', message: '请先填写 API Key 再测试。' }
      }));
      return;
    }

    setTestState((current) => ({
      ...current,
      [testKey]: { status: 'testing', message: '正在检测连通性...' }
    }));

    try {
      const result = await testTranslationProfile({
        ...draftPayload(draft),
        sourceLang,
        targetLang
      });
      setProviderHealth(result.providerHealth ?? {});
      setTestState((current) => ({
        ...current,
        [testKey]: {
          status: result.ok ? 'ok' : 'failed',
          message: result.ok ? `检测通过：${result.translatedText || result.message}` : result.message || '检测失败',
          elapsedMs: result.elapsedMs,
          providerChain: result.providerChain
        }
      }));
    } catch (error) {
      setTestState((current) => ({
        ...current,
        [testKey]: {
          status: 'failed',
          message: error instanceof Error ? error.message : '检测失败'
        }
      }));
      void fetchTranslationHealth().then((health) => setProviderHealth(health ?? {})).catch(() => undefined);
    }
  };

  return (
    <section className="card translation-services-card">
      <div className="section-head">
        <div>
          <p className="eyebrow">翻译服务</p>
          <h3>翻译服务列表</h3>
          <p className="subtle-text">左侧选择服务，右侧只编辑当前服务需要的配置。免填 Key 不等于永久免费，实际可用性以连通性检测为准。</p>
        </div>
      </div>

      <div className="translation-service-layout">
        <div className="translation-service-list">
          {loading ? <div className="empty-card">正在读取本机翻译服务...</div> : null}

          {extraSavedProfiles.length ? (
            <div className="service-group">
              <p className="service-group-title">我的自定义配置</p>
              {extraSavedProfiles.map((profile) => (
                <button
                  key={profile.id}
                  type="button"
                  className={`service-row${draft.id === profile.id ? ' active' : ''}`}
                  onClick={() => onSelectProfile(profile)}
                >
                  <span className="service-avatar">{profile.name.slice(0, 1).toUpperCase()}</span>
                  <span className="service-row-main">
                    <strong>{profile.name}</strong>
                    <small>{profile.baseUrl || providerLabels[profile.provider] || profile.provider}</small>
                  </span>
                  <span className={`service-status${profile.isDefault ? ' default' : ''}`}>{statusLabel(profile)}</span>
                </button>
              ))}
            </div>
          ) : null}

          {(['free', 'paid', 'custom'] as TranslationServiceTier[]).map((tier) => (
            <div key={tier} className="service-group">
              <p className="service-group-title">{tierLabels[tier]}</p>
              {groupedCatalog[tier].map((service) => {
                const saved = savedByService.get(serviceKey(service.provider, service.baseUrl, service.model));
                const isActive = draft.id ? draft.id === saved?.id : !activeProfile && draft.name === service.name;
                return (
                  <button
                    key={service.id}
                    type="button"
                    className={`service-row${isActive ? ' active' : ''}`}
                    onClick={() => onSelectPreset(service)}
                  >
                    <span className="service-avatar">{service.name.slice(0, 1).toUpperCase()}</span>
                    <span className="service-row-main">
                      <strong>{service.name}</strong>
                      <small>{service.description}</small>
                    </span>
                    <span className={`service-status${saved?.isDefault ? ' default' : ''}`}>{statusLabel(saved, service.badge)}</span>
                  </button>
                );
              })}
            </div>
          ))}
        </div>

        <div className="translation-service-detail">
          <form className="settings-form service-form" onSubmit={onSubmit}>
            <div className="service-form-head full-width">
              <div>
                <p className="eyebrow">{draft.id ? '编辑已保存服务' : '配置并保存服务'}</p>
                <h3>{draft.name}</h3>
                {draft.description ? <p className="subtle-text">{draft.description}</p> : null}
              </div>
              <div className="inline-actions">
                <button type="button" className="text-button" onClick={() => void onTestDraft()}>
                  {currentTestState?.status === 'testing' ? '检测中...' : '测试连通性'}
                </button>
                {draft.id ? (
                  <button type="button" className="text-button" onClick={() => void onSetDefault(draft.id!)}>
                    设为优先
                  </button>
                ) : null}
              </div>
            </div>

            {currentTestState ? (
              <div className={`test-result full-width ${currentTestState.status}`}>
                <strong>{currentTestState.message}</strong>
                <span>
                  {currentTestState.providerChain?.length ? `链路：${currentTestState.providerChain.join(' > ')}` : '链路：当前服务'}
                  {currentTestState.elapsedMs ? ` · ${currentTestState.elapsedMs}ms` : ''}
                </span>
              </div>
            ) : null}

            <div className="service-health-strip full-width">
              <span>当前服务：{providerLabel}</span>
              <span>健康状态：{healthSummary}</span>
              <span>{draft.requiresApiKey ? '需要用户 API Key' : '免填 Key，依赖服务端额度'}</span>
            </div>

            <label className="field">
              <span>服务名称</span>
              <input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} required />
            </label>

            <div className="field">
              <span>服务类型</span>
              <div className="readonly-field">{providerLabel} / {tierLabels[draft.tier]}</div>
            </div>

            <label className="field">
              <span>Base URL</span>
              {canEditEndpoint ? (
                <input
                  value={draft.baseUrl}
                  onChange={(event) => setDraft({ ...draft, baseUrl: event.target.value })}
                  placeholder="https://api.example.com/v1"
                />
              ) : (
                <div className="readonly-field">由服务端配置</div>
              )}
            </label>

            <label className="field">
              <span>模型</span>
              {canEditEndpoint ? (
                <input value={draft.model} onChange={(event) => setDraft({ ...draft, model: event.target.value })} required />
              ) : (
                <div className="readonly-field">由服务端配置</div>
              )}
            </label>

            <div className="field full-width">
              <span>API Key</span>
              {canEditApiKey ? (
                <input
                  type="password"
                  value={draft.apiKey}
                  onChange={(event) => setDraft({ ...draft, apiKey: event.target.value })}
                  placeholder="填入自己的 API Key"
                  required
                />
              ) : (
                <div className="readonly-field">该服务不需要用户填写 API Key，实际可用性取决于服务端额度。</div>
              )}
            </div>

            <label className="field full-width">
              <span>自定义 Prompt</span>
              <textarea
                value={draft.prompt}
                onChange={(event) => setDraft({ ...draft, prompt: event.target.value })}
                rows={6}
                placeholder={defaultTranslationPrompt}
              />
            </label>

            <div className="inline-actions full-width service-form-actions">
              <label className="switch-row compact-switch">
                <span>{draft.enabled ? '保存后启用' : '保存后停用'}</span>
                <input
                  type="checkbox"
                  checked={draft.enabled}
                  onChange={(event) => setDraft({ ...draft, enabled: event.target.checked })}
                />
              </label>
              {activeProfile ? (
                <button type="button" className="secondary-button" onClick={() => void onToggleEnabled(activeProfile)}>
                  {activeProfile.enabled ? '停用服务' : '启用服务'}
                </button>
              ) : null}
              {draft.id ? (
                <button type="button" className="text-button danger" onClick={() => void onDelete(draft.id!)}>
                  删除
                </button>
              ) : null}
            </div>

            <button type="submit" className="primary-button">
              保存翻译服务
            </button>
          </form>
        </div>
      </div>
    </section>
  );
}
