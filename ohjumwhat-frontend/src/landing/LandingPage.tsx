import GoogleSignInLink from '../components/GoogleSignInLink.tsx'
import Logo from '../components/Logo.tsx'
import PollPreview from '../components/PollPreview.tsx'
import { buttonClass } from '../lib/ui.ts'

/**
 * 로그인하지 않은 방문자가 /에서 보는 소개 페이지(Figma 00-W·D00-W).
 * 빌드할 때 HTML로 미리 그려 landing.html에 넣는다(vite.config.ts의 landingPage). 검색 로봇이 JS 없이 읽게 하려는 것이라
 * 브라우저에서 React를 다시 붙이지 않는다. 그래서 상태·이벤트 없이 링크만 둔다.
 * 로그인했으면 서버가 이 페이지 대신 앱(index.html)을 준다(백엔드 common/RootPageController).
 */
const container = 'mx-auto max-w-3xl px-4 lg:max-w-[66rem]'
const sectionTitle = 'text-[1.375rem] leading-[1.875rem] font-bold tracking-tight lg:text-[1.75rem] lg:leading-[2.375rem]'

const features = [
  { icon: '👥', title: '메뉴별로 팀이 모여요', body: '가장 많이 나온 메뉴 하나로 몰지 않아요. 메뉴마다 같이 갈 사람이 정해져요.' },
  { icon: '⏰', title: '정기 투표', body: '평일 11시처럼 정해 두면 그 시각에 투표가 저절로 열리고 마감돼요.' },
  { icon: '📍', title: '근처 식당 찾기', body: '조직 위치 근처 식당을 찾아 메뉴에 붙이고, 지도에서 거리와 도보 시간을 봐요.' },
  { icon: '💬', title: '댓글과 채팅', body: '메뉴마다 댓글을, 투표마다 채팅을 열어 어디 갈지 이야기해요.' },
  { icon: '📊', title: '통계와 메뉴 추천', body: '우리 조직이 자주 먹은 메뉴를 보고, 요즘 안 먹은 메뉴를 추천받아요.' },
  { icon: '🏆', title: '메뉴 메이커 랭킹', body: '채택된 메뉴를 많이 올린 사람을 주간·월간 랭킹으로 보여줘요.' },
]

const steps = [
  { title: '조직 만들고 초대하기', body: '조직을 만들고 초대 링크를 메신저에 공유해요.' },
  { title: '메뉴 올리고 고르기', body: '먹고 싶은 메뉴를 올리고 같이 갈 메뉴를 골라요. 오늘은 패스해도 돼요.' },
  { title: '마감되면 팀 확인', body: '마감되면 메뉴별 팀이 정해져요. 결과를 복사해 메신저에 붙여요.' },
]

export default function LandingPage() {
  return (
    <>
      <div className="bg-bg-brand-soft">
        <header className={`${container} flex items-center justify-between py-4 lg:py-5`}>
          <a href="/" aria-label="오점왓 처음으로">
            <Logo />
          </a>
          <a href="/login" className={buttonClass('secondary')}>
            로그인
          </a>
        </header>
        <section className={`${container} flex flex-col gap-8 pt-6 pb-14 lg:grid lg:grid-cols-[minmax(0,1fr)_27.5rem] lg:items-center lg:gap-16 lg:pt-16 lg:pb-24`}>
          <div>
            <p className="text-sm font-medium text-text-brand">점심·저녁 메뉴 투표</p>
            <h1 className="mt-4 text-[1.875rem] leading-10 font-bold tracking-tight lg:text-[2.5rem] lg:leading-[3.25rem]">
              오늘 점심 뭐 먹지?
              <br />
              투표로 정하고 같이 가요
            </h1>
            <p className="mt-4 text-text-secondary">
              먹고 싶은 메뉴를 올리고 같이 갈 사람을 모아요.
              <br />
              결과는 1등 메뉴 하나가 아니라 메뉴별로 모인 팀이에요.
            </p>
            <GoogleSignInLink className="mt-6 lg:w-[22.5rem]">구글 계정으로 시작하기</GoogleSignInLink>
            <p className="mt-4 text-xs text-text-tertiary">구글 계정 하나로 바로 시작해요. 따로 가입할 것은 없어요.</p>
          </div>
          <PollPreview />
        </section>
      </div>

      <main>
        <section aria-labelledby="features" className={`${container} py-14 lg:py-20`}>
          <h2 id="features" className={sectionTitle}>
            점심 정하는 데 필요한 것만 담았어요
          </h2>
          <p className="mt-2 text-text-secondary">회사·학교·동아리처럼 매일 같이 밥을 먹는 사람들을 위해 만들었어요.</p>
          <ul className="mt-6 grid gap-3 lg:mt-8 lg:grid-cols-3 lg:gap-6">
            {features.map((feature) => (
              <li key={feature.title} className="rounded-2xl border border-border-default bg-bg-surface p-6">
                <span aria-hidden className="flex size-12 items-center justify-center rounded-xl bg-bg-brand-soft text-2xl">
                  {feature.icon}
                </span>
                <h3 className="mt-3 font-bold">{feature.title}</h3>
                <p className="mt-2 text-sm text-text-secondary">{feature.body}</p>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="steps" className="bg-bg-surface">
          <div className={`${container} py-14 lg:py-20`}>
            <h2 id="steps" className={sectionTitle}>
              세 단계면 충분해요
            </h2>
            <ol className="mt-6 grid gap-6 lg:mt-8 lg:grid-cols-3">
              {steps.map((step, index) => (
                <li key={step.title}>
                  <span aria-hidden className="flex size-9 items-center justify-center rounded-full bg-bg-brand font-bold text-text-on-brand">
                    {index + 1}
                  </span>
                  <h3 className="mt-3 font-bold">{step.title}</h3>
                  <p className="mt-2 text-sm text-text-secondary">{step.body}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section aria-labelledby="start" className="bg-bg-brand-soft">
          <div className={`${container} flex flex-col items-center py-14 text-center lg:py-20`}>
            <h2 id="start" className={sectionTitle}>
              오늘 점심, 투표로 정해요
            </h2>
            <p className="mt-3 text-text-secondary">조직을 만들고 초대 링크만 보내면 오늘부터 쓸 수 있어요.</p>
            <GoogleSignInLink className="mt-6 lg:w-[22.5rem]">구글 계정으로 시작하기</GoogleSignInLink>
          </div>
        </section>
      </main>

      <footer className="border-t border-border-default">
        <p className={`${container} py-6 text-xs text-text-tertiary lg:py-8`}>© 2026 오점왓 · 회사·학교·동아리의 점심·저녁 메뉴 투표</p>
      </footer>
    </>
  )
}
