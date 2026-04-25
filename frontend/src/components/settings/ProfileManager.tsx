import { type FormEvent, useEffect, useState } from 'react';
import { deleteTranslationProfile, listTranslationProfiles, saveTranslationProfile } from '../../services/storage/translationProfiles';
import { usePreferencesStore } from '../../store/usePreferencesStore';
import type { TranslationProfile, TranslationProvider } from '../../types/media';

const emptyForm = {
  name: '',
  provider: 'openai-compatible' as TranslationProvider,
  baseUrl: '',
  model: '',
  apiKey: ''
};

export function ProfileManager() {
  const [profiles, setProfiles] = useState<TranslationProfile[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [loading, setLoading] = useState(true);
  const selectedProfileId = usePreferencesStore((state) => state.selectedProfileId);
  const setSelectedProfileId = usePreferencesStore((state) => state.setSelectedProfileId);

  const reload = async () => {
    setLoading(true);
    const rows = await listTranslationProfiles();
    setProfiles(rows);
    setLoading(false);
  };

  useEffect(() => {
    void reload();
  }, []);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await saveTranslationProfile(form);
    setForm(emptyForm);
    await reload();
  };

  const onDelete = async (profileId: string) => {
    await deleteTranslationProfile(profileId);
    if (selectedProfileId === profileId) {
      setSelectedProfileId(undefined);
    }
    await reload();
  };

  return (
    <div className="settings-stack">
      <section className="card">
        <div className="section-head">
          <div>
            <p className="eyebrow">模型配置</p>
            <h3>保存到本机</h3>
          </div>
          <span className="subtle-text">API Key 会先在浏览器本地加密后，再写入 IndexedDB。</span>
        </div>

        <form className="settings-form" onSubmit={onSubmit}>
          <label className="field">
            <span>配置名称</span>
            <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required />
          </label>

          <label className="field">
            <span>Provider</span>
            <select
              value={form.provider}
              onChange={(event) => setForm({ ...form, provider: event.target.value as TranslationProvider })}
            >
              <option value="openai-compatible">OpenAI Compatible</option>
              <option value="deepseek">DeepSeek</option>
              <option value="doubao">Doubao</option>
            </select>
          </label>

          <label className="field">
            <span>Base URL</span>
            <input value={form.baseUrl} onChange={(event) => setForm({ ...form, baseUrl: event.target.value })} required />
          </label>

          <label className="field">
            <span>Model</span>
            <input value={form.model} onChange={(event) => setForm({ ...form, model: event.target.value })} required />
          </label>

          <label className="field full-width">
            <span>API Key</span>
            <input
              type="password"
              value={form.apiKey}
              onChange={(event) => setForm({ ...form, apiKey: event.target.value })}
              required
            />
          </label>

          <button type="submit" className="primary-button">
            保存配置
          </button>
        </form>
      </section>

      <section className="card">
        <div className="section-head">
          <div>
            <p className="eyebrow">已保存配置</p>
            <h3>选择默认翻译模型</h3>
          </div>
        </div>

        {loading ? (
          <div className="empty-card">正在读取本机配置...</div>
        ) : profiles.length ? (
          <div className="profile-grid">
            {profiles.map((profile) => (
              <article key={profile.id} className={`profile-card${selectedProfileId === profile.id ? ' active' : ''}`}>
                <div>
                  <strong>{profile.name}</strong>
                  <p>{profile.provider}</p>
                  <p>{profile.model}</p>
                </div>
                <div className="inline-actions">
                  <button type="button" className="text-button" onClick={() => setSelectedProfileId(profile.id)}>
                    设为默认
                  </button>
                  <button type="button" className="text-button danger" onClick={() => void onDelete(profile.id)}>
                    删除
                  </button>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <div className="empty-card">还没有保存任何模型配置。</div>
        )}
      </section>
    </div>
  );
}
