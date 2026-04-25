import { ProfileManager } from '../components/settings/ProfileManager';
import { usePreferencesStore } from '../store/usePreferencesStore';

export function SettingsPage() {
  const appearance = usePreferencesStore((state) => state.appearance);
  const setAppearance = usePreferencesStore((state) => state.setAppearance);
  const autoDownload = usePreferencesStore((state) => state.autoDownload);
  const setAutoDownload = usePreferencesStore((state) => state.setAutoDownload);
  const sourceLang = usePreferencesStore((state) => state.sourceLang);
  const targetLang = usePreferencesStore((state) => state.targetLang);
  const setLanguagePair = usePreferencesStore((state) => state.setLanguagePair);

  return (
    <div className="settings-grid">
      <section className="card settings-stack">
        <div className="section-head">
          <div>
            <p className="eyebrow">界面设置</p>
            <h3>自适应主题</h3>
          </div>
        </div>

        <div className="segmented large">
          {[
            { key: 'system', label: '跟随系统' },
            { key: 'light', label: '浅色模式' },
            { key: 'dark', label: '深色模式' }
          ].map((item) => (
            <button
              key={item.key}
              type="button"
              className={appearance === item.key ? 'active' : ''}
              onClick={() => setAppearance(item.key as 'system' | 'light' | 'dark')}
            >
              {item.label}
            </button>
          ))}
        </div>

        <label className="switch-row">
          <span>任务完成后自动下载视频</span>
          <input type="checkbox" checked={autoDownload} onChange={(event) => setAutoDownload(event.target.checked)} />
        </label>

        <div className="settings-form">
          <label className="field">
            <span>源语言</span>
            <input value={sourceLang} onChange={(event) => setLanguagePair(event.target.value, targetLang)} />
          </label>

          <label className="field">
            <span>目标语言</span>
            <input value={targetLang} onChange={(event) => setLanguagePair(sourceLang, event.target.value)} />
          </label>
        </div>
      </section>

      <ProfileManager />
    </div>
  );
}
