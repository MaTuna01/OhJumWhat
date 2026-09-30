import { createBrowserRouter } from 'react-router'
import AppLayout from './components/AppLayout.tsx'
import OrgLayout from './components/OrgLayout.tsx'
import RequireAuth from './components/RequireAuth.tsx'
import InvitePage from './pages/InvitePage.tsx'
import LoginPage from './pages/LoginPage.tsx'
import MyPage from './pages/MyPage.tsx'
import OrgHomePage from './pages/OrgHomePage.tsx'
import OrgSettingsPage from './pages/OrgSettingsPage.tsx'
import PollDetailPage from './pages/PollDetailPage.tsx'
import RootRedirect from './pages/RootRedirect.tsx'
import SchedulesPage from './pages/SchedulesPage.tsx'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/', element: <RootRedirect /> },
      {
        element: <AppLayout />,
        children: [
          { path: '/invite/:token', element: <InvitePage /> },
          { path: '/me', element: <MyPage /> },
          {
            // 조직 화면 공통: 조직 조회(방문 기록)·404 처리·탭
            path: '/orgs/:orgId',
            element: <OrgLayout />,
            children: [
              { index: true, element: <OrgHomePage /> },
              { path: 'polls/:pollId', element: <PollDetailPage /> },
              { path: 'schedules', element: <SchedulesPage /> },
              { path: 'settings', element: <OrgSettingsPage /> },
            ],
          },
        ],
      },
    ],
  },
])
