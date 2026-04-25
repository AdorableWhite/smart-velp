import { useLocation } from 'react-router-dom';
import { usePreferencesStore } from '../../store/usePreferencesStore';

const routeMeta: Record<string, { title: string; description: string }> = {
  '/': {
    title: '今日学习',
    description: '从链接或本地文件开始，快速进入学习状态。'
  },
  '/library': {
    title: '资料库',
    description: '管理在线任务、本地导入内容和最近处理结果。'
  },
  '/settings': {
    title: '设置',
    description: '管理主题、翻译方向和本机模型配置。'
  }
};

export function HeaderBar() {
  const location = useLocation();
  const appearance = usePreferencesStore((state) => state.appearance);
  const setAppearance = usePreferencesStore((state) => state.setAppearance);

  const meta = routeMeta[location.pathname] ?? {
    title: '学习页',
    description: '专注观看、跟读、切换字幕与节奏控制。'
  };

  return (
    <header className="topbar">
      <div>
        <p className="eyebrow">Smart VELP</p>
        <h2>{meta.title}</h2>
        <p className="header-copy">{meta.description}</p>
      </div>

      <div className="theme-toggle-group" role="group" aria-label="主题切换">
        {[
          { value: 'system', label: '跟随系统' },
          { value: 'light', label: '浅色' },
          { value: 'dark', label: '深色' }
        ].map((item) => (
          <button
            key={item.value}
            type="button"
            className={`chip-button${appearance === item.value ? ' active' : ''}`}
            onClick={() => setAppearance(item.value as 'system' | 'light' | 'dark')}
          >
            {item.label}
          </button>
        ))}
      </div>
    </header>
  );
}

