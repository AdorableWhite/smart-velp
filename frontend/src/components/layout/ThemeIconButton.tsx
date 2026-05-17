import { usePreferencesStore } from '../../store/usePreferencesStore';
import type { AppearanceMode } from '../../types/media';

const appearanceOptions: Record<
  AppearanceMode,
  { icon: string; label: string; next: AppearanceMode; nextLabel: string }
> = {
  system: { icon: 'A', label: '跟随系统', next: 'light', nextLabel: '浅色' },
  light: { icon: '☀', label: '浅色', next: 'dark', nextLabel: '深色' },
  dark: { icon: '☾', label: '深色', next: 'system', nextLabel: '跟随系统' }
};

interface ThemeIconButtonProps {
  className?: string;
}

export function ThemeIconButton({ className = '' }: ThemeIconButtonProps) {
  const appearance = usePreferencesStore((state) => state.appearance);
  const setAppearance = usePreferencesStore((state) => state.setAppearance);
  const option = appearanceOptions[appearance];

  return (
    <button
      type="button"
      className={`icon-button theme-icon-button ${className}`.trim()}
      title={`当前主题：${option.label}。点击切换为${option.nextLabel}`}
      aria-label={`当前主题：${option.label}。点击切换为${option.nextLabel}`}
      onClick={() => setAppearance(option.next)}
    >
      {option.icon}
    </button>
  );
}
