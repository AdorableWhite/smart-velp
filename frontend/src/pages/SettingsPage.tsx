import { ThemeIconButton } from '../components/layout/ThemeIconButton';
import { ProfileManager } from '../components/settings/ProfileManager';
import { usePreferencesStore } from '../store/usePreferencesStore';

const commonLanguages = [
  { value: 'en', label: '英语' },
  { value: 'zh-CN', label: '简体中文' },
  { value: 'zh-Hant', label: '繁体中文' },
  { value: 'ja', label: '日语' },
  { value: 'ko', label: '韩语' },
  { value: 'fr', label: '法语' },
  { value: 'de', label: '德语' },
  { value: 'es', label: '西班牙语' },
  { value: 'ru', label: '俄语' },
  { value: 'pt', label: '葡萄牙语' }
];

export function SettingsPage() {
  const sourceLang = usePreferencesStore((state) => state.sourceLang);
  const targetLang = usePreferencesStore((state) => state.targetLang);
  const setLanguagePair = usePreferencesStore((state) => state.setLanguagePair);

  return (
    <div className="settings-page">
      <section className="card settings-compact-card">
        <div className="section-head">
          <div>
            <p className="eyebrow">基础设置</p>
            <h3>主题与翻译方向</h3>
          </div>
          <ThemeIconButton />
        </div>

        <div className="settings-form compact-settings-form">
          <label className="field">
            <span>源语言</span>
            <select value={sourceLang} onChange={(event) => setLanguagePair(event.target.value, targetLang)}>
              {commonLanguages.map((language) => (
                <option key={language.value} value={language.value}>
                  {language.label}
                </option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>目标语言</span>
            <select value={targetLang} onChange={(event) => setLanguagePair(sourceLang, event.target.value)}>
              {commonLanguages.map((language) => (
                <option key={language.value} value={language.value}>
                  {language.label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </section>

      <ProfileManager />
    </div>
  );
}
