import { NavLink, Outlet } from 'react-router-dom';
import { HeaderBar } from './HeaderBar';

const navigation = [
  { to: '/', label: '首页' },
  { to: '/library', label: '资料库' },
  { to: '/settings', label: '设置' }
];

export function AppShell() {
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand-card">
          <div className="brand-pill">PWA v2</div>
          <h1>Smart VELP</h1>
          <p>为视频学习重新整理的一套跨屏体验，支持在线与本地两条学习入口。</p>
        </div>

        <nav className="nav-list" aria-label="主导航">
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
              end={item.to === '/'}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-note">
          <strong>本轮重点</strong>
          <span>React + TypeScript + PWA 基座已接入，页面会对手机、平板、桌面做自适应重排。</span>
        </div>
      </aside>

      <div className="content-frame">
        <HeaderBar />
        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

