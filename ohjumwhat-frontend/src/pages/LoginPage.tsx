import { Navigate, useSearchParams } from 'react-router'
import GoogleSignInLink from '../components/GoogleSignInLink.tsx'
import Logo, { LogoMark } from '../components/Logo.tsx'
import PollPreview from '../components/PollPreview.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { useMe } from '../queries/me.ts'

export default function LoginPage() {
  const me = useMe()
  const [searchParams] = useSearchParams()
  const error = searchParams.get('error')
  useDocumentTitle('로그인')

  if (me.isSuccess) {
    return <Navigate to="/" replace />
  }

  // 모바일(Figma 01)은 가운데 한 줄, 데스크톱(D01)은 왼쪽 소개 + 오른쪽 로그인으로 나눈다.
  return (
    <main className="min-h-dvh lg:grid lg:grid-cols-2">
      <Intro />
      <div className="flex min-h-dvh items-center justify-center px-4">
        <div className="w-full max-w-sm text-center lg:max-w-[22.5rem] lg:text-left">
          <div className="lg:hidden">
            <div className="flex justify-center">
              <LogoMark size={72} />
            </div>
            <h1 className="mt-3 text-4xl font-black tracking-tight text-text-brand">오점왓</h1>
            <p className="mt-3 text-text-secondary">
              오늘 점심 뭐 먹지?
              <br />
              먹고 싶은 메뉴를 올리고 같이 갈 사람을 모아요.
            </p>
          </div>
          <div className="hidden lg:block">
            <h1 className="text-2xl font-bold tracking-tight">시작하기</h1>
            <p className="mt-2 text-sm text-text-secondary">구글 계정 하나로 바로 시작해요.</p>
          </div>

          {error != null && (
            <p role="alert" className="mt-6 rounded-lg bg-bg-danger-soft px-4 py-3 text-sm text-text-danger">
              {error === 'blocked' ? '이 계정은 관리자가 이용을 제한했어요. 문의는 서비스 관리자에게 해 주세요.' : '로그인하지 못했어요. 다시 시도해 주세요.'}
            </p>
          )}

          <GoogleSignInLink className="mt-8 lg:mt-6">구글 계정으로 계속하기</GoogleSignInLink>
          <p className="mt-3 text-xs text-text-tertiary">
            회사·학교 등 조직의 초대 링크로 들어왔다면
            <br />
            로그인 후 초대 화면으로 돌아가요.
          </p>
        </div>
      </div>
    </main>
  )
}

/** 데스크톱 왼쪽 소개(Figma D01): 서비스 설명과 투표 화면 미리보기. 미리보기는 눌리지 않는다(inert). */
function Intro() {
  return (
    <section aria-label="오점왓 소개" className="hidden bg-bg-brand-soft pr-24 pl-30 lg:flex lg:flex-col lg:justify-center">
      <Logo size={56} />
      <p className="mt-10 text-2xl font-bold tracking-tight">오늘 점심 뭐 먹지?</p>
      <p className="mt-2 text-text-secondary">
        먹고 싶은 메뉴를 올리고 같이 갈 사람을 모아요.
        <br />
        결과는 1등 메뉴 하나가 아니라 메뉴별로 모인 팀이에요.
      </p>
      <PollPreview className="mt-10 w-full max-w-[27.5rem]" />
    </section>
  )
}
