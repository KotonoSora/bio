import { useTranslation } from 'react-i18next';
import { NavLink, Route, Routes } from 'react-router';
import { cn } from '@resume-builder/ui';
import AuthError from './pages/AuthError';
import Dashboard from './pages/Dashboard';
import Login from './pages/Login';
import MfeMount from './mfe/MfeMount';
import NotFound from './pages/NotFound';
import RecoveryLanding from './pages/RecoveryLanding';
import Resumes from './pages/Resumes';
import WelcomeUsername from './pages/WelcomeUsername';
const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
    isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
  );

export default function App() {
  const { t } = useTranslation();
  return (
    <div className="min-h-svh">
      <header className="border-b">
        <div className="mx-auto flex max-w-4xl items-center gap-1 px-6 py-3">
          <span className="mr-2 font-heading text-base font-semibold">Resume Builder</span>
          <nav className="flex items-center gap-1">
            <NavLink to="/" end className={navLinkClass}>
              {t('nav.dashboard')}
            </NavLink>
            <NavLink to="/resumes" className={navLinkClass}>
              {t('nav.resumes')}
            </NavLink>
            <NavLink to="/pricing" className={navLinkClass}>
              {t('nav.pricing')}
            </NavLink>
            <NavLink to="/account/settings" className={navLinkClass}>
              {t('nav.settings')}
            </NavLink>
          </nav>
        </div>
      </header>
      <div className="mx-auto max-w-4xl px-6 py-8">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/resumes" element={<Resumes />} />
          <Route path="/pricing" element={<MfeMount name="plans" />} />
          <Route path="/login" element={<Login />} />
          <Route path="/auth/error" element={<AuthError />} />
          <Route path="/welcome/username" element={<WelcomeUsername />} />
          <Route path="/account/settings" element={<MfeMount name="account" />} />
          <Route path="/recovery/:token" element={<RecoveryLanding />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </div>
    </div>
  );
}
