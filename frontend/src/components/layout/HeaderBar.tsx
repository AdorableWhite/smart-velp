import { useLocation } from 'react-router-dom';
import { ThemeIconButton } from './ThemeIconButton';

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

      <ThemeIconButton />
    </header>
  );
}
