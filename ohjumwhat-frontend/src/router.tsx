import { createBrowserRouter } from 'react-router'
import InvitePage from './pages/InvitePage.tsx'
import LoginPage from './pages/LoginPage.tsx'
import MyPage from './pages/MyPage.tsx'
import OrgHomePage from './pages/OrgHomePage.tsx'
import OrgSettingsPage from './pages/OrgSettingsPage.tsx'
import PollDetailPage from './pages/PollDetailPage.tsx'
import RootRedirect from './pages/RootRedirect.tsx'
import SchedulesPage from './pages/SchedulesPage.tsx'

export const router = createBrowserRouter([
  { path: '/', element: <RootRedirect /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/invite/:token', element: <InvitePage /> },
  { path: '/me', element: <MyPage /> },
  { path: '/orgs/:orgId', element: <OrgHomePage /> },
  { path: '/orgs/:orgId/polls/:pollId', element: <PollDetailPage /> },
  { path: '/orgs/:orgId/schedules', element: <SchedulesPage /> },
  { path: '/orgs/:orgId/settings', element: <OrgSettingsPage /> },
])
