import { Link, Outlet } from 'react-router'
import { usePushSync } from '../hooks/usePushSync.ts'
import GuestbookWarningDialog from './GuestbookWarningDialog.tsx'
import LetterButton from './LetterButton.tsx'
import { LetterComposerProvider } from './LetterComposer.tsx'
import Logo from './Logo.tsx'
import NoticeBell from './NoticeBell.tsx'
import OrgSwitcher from './OrgSwitcher.tsx'
import ProfileMenu from './ProfileMenu.tsx'
import UpdateToast from './UpdateToast.tsx'

// 콘텐츠 폭: 모바일·태블릿은 768px, 데스크톱(lg)은 Figma 데스크톱 와이어프레임과 같은 1024px(+ 좌우 여백)
const container = 'mx-auto max-w-3xl px-4 lg:max-w-[66rem]'

export default function AppLayout() {
  // 웹 푸시: 켜 둔 기기를 다시 등록하고, 서비스 워커가 알린 푸시·알림 클릭을 화면에 반영한다.
  usePushSync()
  return (
    // 쪽지 쓰기는 어느 화면에서든(멤버 프로필, 쪽지함) 열 수 있게 앱 전체에 하나만 둔다.
    <LetterComposerProvider>
      <div className="min-h-dvh">
        <header className="sticky top-0 z-10 border-b border-border-default bg-bg-surface/90 backdrop-blur">
          <div className={`${container} flex h-14 items-center gap-2`}>
            <Link to="/" aria-label="오점왓 홈" className="shrink-0 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand">
              <Logo size={28} />
            </Link>
            <OrgSwitcher />
            <div className="flex-1" />
            <LetterButton />
            <NoticeBell />
            <ProfileMenu />
          </div>
        </header>
        <main className={`${container} py-6 lg:pt-8 lg:pb-10`}>
          <Outlet />
        </main>
        <UpdateToast />
        {/* 관리자가 내 방명록 글을 제한했으면 어느 화면에서든 한 번 알린다(Figma 07-M4). */}
        <GuestbookWarningDialog />
      </div>
    </LetterComposerProvider>
  )
}
