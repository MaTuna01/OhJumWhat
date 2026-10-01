import { Navigate, useSearchParams } from 'react-router'
import Logo, { LogoMark } from '../components/Logo.tsx'
import OptionCard from '../components/OptionCard.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { useMe } from '../queries/me.ts'
import type { Person, PollOption } from '../queries/polls.ts'

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

          {/* 서버의 OAuth2 로그인 시작 주소. 로그인 후 서버가 /로 돌려보낸다. */}
          <a
            href="/oauth2/authorization/google"
            className="mt-8 flex w-full items-center justify-center gap-3 rounded-xl border border-border-strong bg-bg-surface px-4 py-3 font-medium shadow-sm hover:bg-bg-subtle focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand lg:mt-6"
          >
            <GoogleLogo />
            구글 계정으로 계속하기
          </a>
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

const previewMe: Person = { userId: 1, name: '김오점', profileImageUrl: null }
const person = (userId: number, name: string): Person => ({ userId, name, profileImageUrl: null })
const previewOptions: PollOption[] = [
  { id: 1, name: '돈까스', link: null, createdBy: previewMe, voters: [previewMe, person(2, '김철수'), person(3, '정하늘')], mine: true, deletable: false },
  { id: 2, name: '김치찌개', link: null, createdBy: person(4, '이영희'), voters: [person(5, '박민수'), person(6, '최지우')], mine: false, deletable: false },
]

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
      <div inert className="mt-10 w-full max-w-[27.5rem] space-y-2.5">
        {previewOptions.map((option) => (
          <OptionCard key={option.id} option={option} meId={previewMe.userId} selected={option.id === 1} />
        ))}
      </div>
    </section>
  )
}

function GoogleLogo() {
  return (
    <svg viewBox="0 0 48 48" className="size-5" aria-hidden>
      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" />
      <path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
      <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z" />
      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" />
    </svg>
  )
}
