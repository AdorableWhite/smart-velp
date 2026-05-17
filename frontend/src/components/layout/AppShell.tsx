import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { HeaderBar } from './HeaderBar';

const navigation = [
  { to: '/', label: '首页' },
  { to: '/library', label: '资料库' },
  { to: '/settings', label: '设置' }
];

export function AppShell() {
  const location = useLocation();
  const isStudyPage = location.pathname.startsWith('/study/');
  const isSettingsPage = location.pathname === '/settings';

  return (
    <div className={`shell${isStudyPage ? ' shell--study' : ''}`}>
      <aside className="sidebar">
        <div className="brand-card">
          <div className="brand-pill">Smart VELP</div>
          <h1>Smart VELP</h1>
          <p>一个把在线视频、本地视频与字幕学习整合到同一工作流里的轻量学习台。</p>
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
          <strong>当前方向</strong>
          <span>持续收口 UI、字幕重译链路与 PWA 体验，让它更像一个真正的学习产品。</span>
        </div>
      </aside>

      <div className="content-frame">
        {isStudyPage || isSettingsPage ? null : <HeaderBar />}
        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
