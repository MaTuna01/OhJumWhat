import { createBrowserRouter } from 'react-router'
import AdminLayout from './components/AdminLayout.tsx'
import AppLayout from './components/AppLayout.tsx'
import OrgLayout from './components/OrgLayout.tsx'
import RequireAuth from './components/RequireAuth.tsx'
import AdminBlocksPage from './pages/admin/AdminBlocksPage.tsx'
import AdminNoticesPage from './pages/admin/AdminNoticesPage.tsx'
import AdminOrgPage from './pages/admin/AdminOrgPage.tsx'
import AdminOrgsPage from './pages/admin/AdminOrgsPage.tsx'
import AdminOverviewPage from './pages/admin/AdminOverviewPage.tsx'
import AdminPollPage from './pages/admin/AdminPollPage.tsx'
import AdminUserPage from './pages/admin/AdminUserPage.tsx'
import AdminUsersPage from './pages/admin/AdminUsersPage.tsx'
import InvitePage from './pages/InvitePage.tsx'
import LoginPage from './pages/LoginPage.tsx'
import MyPage from './pages/MyPage.tsx'
import NotFoundPage from './pages/NotFoundPage.tsx'
import NoticesPage from './pages/NoticesPage.tsx'
import OrgHomePage from './pages/OrgHomePage.tsx'
import OrgSettingsPage from './pages/OrgSettingsPage.tsx'
import OrgStatsPage from './pages/OrgStatsPage.tsx'
import PollDetailPage from './pages/PollDetailPage.tsx'
import RootRedirect from './pages/RootRedirect.tsx'
import RouteErrorPage from './pages/RouteErrorPage.tsx'
import SchedulesPage from './pages/SchedulesPage.tsx'

export const router = createBrowserRouter([
  {
    // 화면을 그리다 예상하지 못한 오류가 나면 이 화면을 보여준다.
    errorElement: <RouteErrorPage />,
    children: [
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
              { path: '/notices', element: <NoticesPage /> },
              {
                // 조직 화면 공통: 조직 조회(방문 기록)·404 처리·탭
                path: '/orgs/:orgId',
                element: <OrgLayout />,
                children: [
                  { index: true, element: <OrgHomePage /> },
                  { path: 'polls/:pollId', element: <PollDetailPage /> },
                  { path: 'schedules', element: <SchedulesPage /> },
                  { path: 'stats', element: <OrgStatsPage /> },
                  { path: 'settings', element: <OrgSettingsPage /> },
                ],
              },
              {
            // 관리자 콘솔: 관리자가 아니면 AdminLayout이 없는 페이지로 보여준다(권한은 서버가 확인).
            path: '/admin',
            element: <AdminLayout />,
            children: [
              { index: true, element: <AdminOverviewPage /> },
              { path: 'users', element: <AdminUsersPage /> },
              { path: 'users/:userId', element: <AdminUserPage /> },
              { path: 'orgs', element: <AdminOrgsPage /> },
              { path: 'orgs/:orgId', element: <AdminOrgPage /> },
              { path: 'polls/:pollId', element: <AdminPollPage /> },
              { path: 'blocks', element: <AdminBlocksPage /> },
              { path: 'notices', element: <AdminNoticesPage /> },
            ],
          },
          { path: '*', element: <NotFoundPage /> },
            ],
          },
        ],
      },
    ],
  },
])
