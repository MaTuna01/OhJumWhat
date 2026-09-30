# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

**오점왓(ohjumwhat)**은 조직 안에서 점심·저녁 메뉴를 투표로 정하는 웹 서비스다. 결과는 "1등 메뉴"가 아니라 **메뉴별 참여자 명단(팀)**이다.

- 기획서(요구사항·화면 7개·ERD·삭제 규칙의 원본): Notion 「점심메뉴 선정」 https://app.notion.com/p/3eb11d838f8d802e81dbfcd702c34692
  기획서에는 서비스명이 가칭 "밥팟"으로 적혀 있지만, 정식 이름은 **오점왓**이다. 코드·설정·화면에서는 오점왓/ohjumwhat을 쓴다.
- 구현 계획·진행 현황·남은 단계는 [docs/PLAN.md](docs/PLAN.md)에 있다. 단계를 마치면 이 문서의 진행 현황도 갱신한다.
- 진행 단계는 같은 페이지의 Tasks DB에 7단계로 등록되어 있다. 단계를 마치면 해당 작업의 상태를 갱신한다.
- git에 올리지 않는 파일(`.env`, `application.yml`, OAuth JSON, 서버 pem 키)의 실제 내용은 Notion 「개발 필요 파일」 페이지(기획서 하위)에 있다.
- 원격 저장소: https://github.com/MaTuna01/OhJumWhat (`main`). 커밋 메시지는 한국어로 쓴다.
  - main에 바로 푸시하지 않고 dev브랜치를 개발 브랜치로 활용하고 기능 개발은 dev브랜치에서 개발할 기능 이름으로 분기하여 개발하며 기능 개발 이 완료되면 dev 브랜치로 PR/merge를 통해 모은다
  - main 승격은 사용자 판단하에 진행한다.
  - 기능 브랜치 이름은 `feature/<기능명>`으로 짓는다(예: `feature/auth`, `feature/organization`). `dev`의 최신 상태에서 분기하고, 완료되면 `gh pr create --base dev`로 PR을 연다.
  - 기능 PR을 `dev`에 머지하는 것은 Claude가 직접 해도 된다. 단, 백엔드·프론트 테스트와 빌드를 통과하고 동작 확인을 마친 뒤, PR 체크리스트를 채우고 머지한다(merge commit, `gh pr merge --merge`). `dev` → `main`은 사용자가 결정한다.

## 명령어

```bash
# 로컬 DB (루트 .env 값 사용)
docker compose -f docker-compose.dev.yml up -d

# 백엔드 (ohjumwhat-backend/) — 테스트는 Testcontainers라 Docker가 켜져 있어야 한다
./gradlew bootRun
./gradlew test
./gradlew test --tests 'com.ohjumwhat.SomeTest'            # 클래스 하나
./gradlew test --tests 'com.ohjumwhat.SomeTest.methodName' # 메서드 하나

# 프론트엔드 (ohjumwhat-frontend/)
npm run dev          # :5173, /api·/oauth2·/login/oauth2·/logout은 :8080으로 프록시
npm run build        # tsc -b && vite build
npm run lint         # oxlint
npm test             # vitest run
npx vitest run src/lib/foo.test.ts -t '케이스 이름'
```

## 설정과 비밀값

- `.env`(루트)와 `ohjumwhat-backend/src/main/resources/application.yml`은 **gitignore 대상**이다. 커밋하는 것은 템플릿인 `.env.example`과 `application.example.yml`뿐이다. 설정 키를 추가하거나 바꾸면 **템플릿도 함께 고친다**.
- `application.yml`에는 `${DB_PASSWORD}` 같은 플레이스홀더만 둔다. `spring.config.import: optional:file:../.env[.properties]`로 로컬 `bootRun`이 루트 `.env`를 읽는다. 운영(Docker)에서는 compose의 `env_file`로 주입한다.
- `ohjumwhat-backend/src/test/resources/application.yml`은 커밋된 테스트 전용 설정이다. 클래스패스에서 main의 `application.yml`보다 먼저 잡혀 이를 가린다. DB 연결은 `TestcontainersConfiguration`의 `@ServiceConnection`(postgres:18-alpine)으로 받는다.
- 구글 OAuth 로컬 리디렉션 URI는 `http://localhost:5173/login/oauth2/code/google`이다(Vite 프록시 경유).

## 아키텍처

현재 3단계(골격·스키마, 구글 로그인·세션, 조직 생성·초대·탈퇴·설정·마이페이지)까지 구현되어 있다. 아래 내용 중 정기 투표 스케줄러와 Docker 배포는 아직 구현 전이며, 구현할 때 이 설계를 따른다.

**동일 출처 구조.** 운영에서는 React 빌드 결과를 Spring Boot jar의 static 리소스로 넣어 이미지 하나로 배포한다(Caddy가 앞단). 개발에서는 Vite가 백엔드 경로를 프록시하는데, `changeOrigin: false`로 Host 헤더를 유지하고 백엔드는 `server.forward-headers-strategy: framework`로 설정한다. 그래서 CORS 설정이 없고, 인증은 JWT 없이 **세션 쿠키**로 한다. 프론트의 `/login`은 SPA 화면이고, Spring의 기본 로그인 페이지는 쓰지 않는다. 운영에서 파일이 없는 화면 경로는 `common/SpaWebConfig`가 `index.html`로 돌려준다.

**인증 흐름**(`auth/SecurityConfig`)
- `/api/**`만 인증이 필요하다. 로그인하지 않았으면 리다이렉트 대신 401을 준다.
- 로그인 버튼은 `/oauth2/authorization/google`로 이동한다. 로그인에 성공하면 서버는 항상 `/`로 보낸다.
- 그다음 어디로 갈지는 프론트 `RootRedirect`가 정한다. `lib/entry.ts` 순서대로 sessionStorage에 기억해 둔 경로(초대 링크) → 최근 조직 → `/me`로 보낸다.
- `GoogleOidcUserService`가 google_sub 기준으로 users를 upsert하고, 세션 principal로 `LoginUser`(users.id 포함)를 둔다. 컨트롤러에서는 `@AuthenticationPrincipal LoginUser`로 받는다.
- CSRF는 `csrf.spa()` 방식이다. `CsrfCookieFilter`가 매 응답에 `XSRF-TOKEN` 쿠키를 내리고, 프론트 `lib/api.ts`가 GET이 아닌 요청에 `X-XSRF-TOKEN` 헤더로 붙인다.
- 로그아웃은 `POST /logout`이고 204를 준다.
- 세션은 Spring Session JDBC로 DB(`spring_session` 테이블, Flyway V2)에 저장한다. 그래서 서버를 재시작·재배포해도 로그인이 유지된다. `SESSION` 쿠키의 유효기간은 30일이다.
- request cache는 꺼 두었다(`NullRequestCache`). 로그인 후에는 항상 `/`로 가고, 로그인하지 않은 요청에는 세션을 만들지 않는다.

**API 규칙**
- 사용자에게 보여줄 오류는 `ApiException`(`notFound`/`badRequest`/`forbidden`/`conflict`)으로 던진다. `GlobalExceptionHandler`가 이를 `{"message": "..."}`로 응답하고, 요청 값 검증 실패(`@Valid`)도 같은 형식으로 준다. 프론트 `api()`는 이 `message`를 `ApiError.message`로 꺼낸다.
- 조직 하위 API는 먼저 `MembershipService.requireMember(orgId, userId)`를 호출한다. 멤버가 아니면 조직이 있는지도 알리지 않도록 404로 응답한다.
- 조회용 DTO가 필요하면 JPQL `select new ...Record(...)`로 바로 만든다(예: `MembershipRepository.findMembers`).

**백엔드 테스트**
- 통합 테스트는 `IntegrationTest`를 상속한다. 컨텍스트와 컨테이너를 공유하고, 테스트가 끝날 때마다 모든 테이블을 TRUNCATE한다.
- 로그인 상태는 `TestAuth.loginAs(user)`로 만든다.
- CSRF가 필요한 요청에는 `TestAuth.xsrf()`를 쓴다. Spring Security의 `csrf()` 헬퍼는 공유 CsrfFilter의 저장소를 세션 방식으로 바꿔 버려서, 이후 테스트에서 쿠키 발급이 깨진다. 그래서 쓰지 않는다.

**스키마는 Flyway가 소유한다.** `db/migration/V*.sql`이 원본이고 JPA는 `ddl-auto: validate`로 검증만 한다. 스키마를 바꿀 때는 기존 마이그레이션을 고치지 말고 새 `V{n}__*.sql`을 추가한 뒤 엔티티를 맞춘다. 컨텍스트 로드 테스트(`OhjumwhatApplicationTests`)가 불일치를 잡아낸다.

**엔티티는 연관관계 매핑 없이 FK를 `Long` ID 필드로 들고 있다**(`organizationId`, `pollId` 등). 조회는 JPQL/쿼리로 조합하고, `open-in-view: false`이므로 지연 로딩에 기대지 않는다. Java 패키지는 도메인별이다(`user`, `organization`, `poll`, `schedule`, `menu`, `vote`, `common`).

**시간은 전부 KST 기준 `Clock` 빈으로 계산한다**(`common/TimeConfig`, `TimeConfig.KST`). "오늘"(`poll_date`), 마감 판정, 정기 투표 시각이 모두 여기에 해당한다. 서비스 코드에서 `LocalDate.now()`나 `Instant.now()`를 직접 부르지 않고 `Clock`을 주입받는다(테스트에서 고정 Clock으로 바꾸기 위함). DB 타임스탬프는 `timestamptz`와 `Instant`로 다룬다.

**도메인 규칙 중 코드만 봐서는 알기 어려운 것**
- 마감은 별도 배치 없이 판정한다. 요청 시점이 `closes_at` 이후면 마감이다(`Poll.isClosed`).
- 정기 투표는 1분 주기 `@Scheduled`가 만든다. `polls(schedule_id, poll_date)` UNIQUE 제약이 중복 생성을 막는다. 제약 위반은 무시하는 것이 의도된 동작이다(서버 재시작 후 누락분도 같은 방식으로 채운다).
- `poll_schedules.days_of_week`는 요일 비트마스크다(월=1 … 일=64, 평일=31). 변환은 `PollSchedule.bitOf`/`runsOn`으로 한다.
- `votes`는 (poll, user)당 한 행이다. 메뉴를 바꾸면 `option_id`만 갱신한다. `option_id`가 NULL이면 "오늘은 패스"다.
- `votes.option_id` FK는 의도적으로 `NO ACTION`이다(RESTRICT 아님). 투표를 CASCADE로 삭제할 때 검사가 문장 끝으로 미뤄지게 하기 위해서다. 참여자가 있는 메뉴를 삭제하지 못하게 막는 검사는 서비스에서 먼저 한다.
- 메뉴를 추가해도 추가한 사람이 자동으로 참여하지 않는다. 메뉴는 추가한 사람만, 참여자가 0명이고 투표가 진행 중일 때만 삭제할 수 있다. 메뉴 이름은 trim해서 저장하고, (poll, name)은 UNIQUE다.
- 조직에는 관리자가 없고 모든 멤버의 권한이 같다. 마지막 멤버가 탈퇴하면 조직을 삭제하고, 하위 데이터는 DB `ON DELETE CASCADE`로 함께 지운다. 정기 규칙을 삭제하면 `polls.schedule_id`만 NULL이 된다.
- 멤버가 탈퇴하면 그 조직의 **진행 중인** 투표에서 그 사람의 votes만 지운다. 마감된 투표 기록은 남긴다.
- 메뉴 자동완성은 별도 테이블 없이 같은 조직 과거 투표의 `menu_options.name`을 중복 없이 조회해서 만든다.

**프론트엔드.** 라우트는 `src/router.tsx` 한 곳에 모여 있다(기획서의 화면 7개 + `/` 진입 분기). `/login`을 뺀 모든 화면은 `RequireAuth`(내 정보 조회가 401이면 경로를 기억하고 로그인 화면으로 보냄) 아래에 있다. API 호출은 `lib/api.ts`의 `api()`로만 하고, 서버 상태 훅과 query key는 `src/queries/`에 둔다. `/orgs/:orgId` 아래 화면은 `OrgLayout`이 조직 조회(방문 기록 갱신), 404 처리, 탭을 맡고, 하위 화면은 `useOrganization(orgId)` 캐시를 그대로 쓴다. 버튼·입력창 스타일은 `lib/ui.ts`(`buttonClass`, `inputClass`)에 있고, 모달은 네이티브 `<dialog>` 기반 `Modal`/`ConfirmDialog`를 쓴다. 다른 쿼리나 뮤테이션이 401을 받으면 `main.tsx`의 캐시 핸들러가 내 정보를 다시 불러오고, 그 결과로 로그인 화면으로 이동한다. 서버 상태는 TanStack Query로 관리한다. 투표 상세 화면은 WebSocket을 쓰지 않고 진행 중일 때 몇 초 간격으로 폴링한다(10~20명 규모). 스타일은 Tailwind v4(`@import 'tailwindcss'`, `@tailwindcss/vite` 플러그인)다.

## 디자인 시스템 (Figma) — 프론트엔드는 이것을 기준으로 개발한다

- Figma 파일: https://www.figma.com/design/w0OIV1khSVnxlf5KfRo6aP/OhJumWhat
  - 「디자인 시스템」 페이지
    - Foundations 프레임: 로고, 컨셉 컬러, 원색 팔레트, 의미 기반 토큰, 타이포그래피, 간격·둥글기·그림자
    - Components 프레임: Button, Badge, Avatar, OptionCard, Input, Logo, TopBar
  - 「와이어프레임」 페이지: 모바일(390px) 화면 12개. 01 로그인부터 07 조직 설정까지와 `-M` 모달
- **새 화면이나 컴포넌트를 만들 때는 먼저 해당 Figma 프레임을 보고 그대로 구현한다.**
  - 디자인과 다르게 구현해야 하면 이유를 PR에 적는다.
  - Figma MCP는 데스크톱 연결(`figma-desktop`)을 쓴다. 원격 Figma MCP 계정에는 이 파일의 편집 권한이 없다.
- **색은 의미 기반 토큰 클래스만 쓴다.**
  - 예: `bg-bg-canvas`, `bg-bg-brand`, `text-text-secondary`, `border-border-default`
  - `stone-*`, `orange-*` 같은 원색 클래스는 화면 코드에서 쓰지 않는다.
  - 토큰은 `ohjumwhat-frontend/src/index.css`의 `@theme`에 있고, Figma `Color` 변수와 이름이 1:1이다(`color/bg/canvas` → `--color-bg-canvas` → `bg-bg-canvas`).
  - 토큰을 추가하거나 바꿀 때는 Figma 변수와 `@theme`를 함께 고친다.
- **글꼴은 Noto Sans KR**(`index.html`에서 Google Fonts로 불러옴)이다.
  - 굵기는 400(본문) / 500(버튼·라벨·배지) / 700(제목·강조) / 900(로고)만 쓴다. `font-semibold`는 쓰지 않는다.
  - 텍스트 스타일 대응: H1=`text-2xl font-bold`, H2=`text-lg font-bold`, H3=`font-bold`, Small=`text-sm`, Caption=`text-xs`.
- **둥글기**: Figma `radius/md·lg·xl·full`(8·12·16·원형)은 Tailwind `rounded-lg·xl·2xl·full`에 대응한다. 입력·버튼 8, 메뉴 12, 카드·모달 16이다.
- **컴포넌트 대응**
  - Button → `components/Button.tsx`, `lib/ui.ts`의 `buttonClass`
  - Input → `lib/ui.ts`의 `inputClass`
  - Avatar → `components/Avatar.tsx`
  - Logo → `components/Logo.tsx`(`Logo`, `LogoMark`)
  - TopBar → `components/AppLayout.tsx`
  - Badge, OptionCard는 4단계에서 만든다.

## 코드 스타일

- Java: 탭 들여쓰기(Spring Initializr 스타일), 주석은 한국어.
- 백엔드는 디버깅과 개발 편의를 위해 Lombok의 `@Slf4j`로 로깅한다. 로그에는 이메일 같은 개인정보 대신 ID를 남긴다.
- TypeScript: 2칸 들여쓰기, 세미콜론 없음, 작은따옴표, 로컬 import에 `.tsx`/`.ts` 확장자를 붙인다(`allowImportingTsExtensions`).
