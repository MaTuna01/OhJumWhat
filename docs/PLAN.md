# 오점왓 구현 계획

> 기획서: Notion 「점심메뉴 선정」 https://app.notion.com/p/3eb11d838f8d802e81dbfcd702c34692
> 작업 관리: 같은 페이지의 Tasks DB (단계별 작업 7개, 선행/후속 관계 연결)
> 마지막 갱신: 2026-10-01 · 운영: https://www.ohjumwhat.cloud (`main` push 시 자동 배포)

## 목표
조직 안에서 점심·저녁 메뉴를 투표로 정하는 웹 서비스 **오점왓(ohjumwhat)**의 MVP를 만든다. 결과는 "1등 메뉴"가 아니라 **메뉴별 참여자 명단(팀)**이다. 메신저 투표에는 열린 투표에 항목을 추가하는 기능 등이 부족해서 전용 도구를 만든다. 사용 규모는 조직당 10~20명이다.

기획서에는 서비스명이 가칭 "밥팟"으로 적혀 있지만, 정식 이름은 **오점왓**이다.

## 진행 현황

| 단계 | 내용 | 상태 | PR |
|---|---|---|---|
| 1 | 프로젝트 골격 | 완료 | 첫 커밋 (`main`) |
| 2 | 인증 (구글 로그인·세션·CSRF) | 완료 | [#1](https://github.com/MaTuna01/OhJumWhat/pull/1) |
| 3 | 조직 (생성·초대·탈퇴·설정·마이페이지) | 완료 | [#2](https://github.com/MaTuna01/OhJumWhat/pull/2) |
| - | 디자인 시스템·와이어프레임 (Figma) + 코드 토큰 적용 | 완료 | [#4](https://github.com/MaTuna01/OhJumWhat/pull/4) |
| 4 | 투표 핵심 (메뉴·참여·현황·결과) | 완료 | [#5](https://github.com/MaTuna01/OhJumWhat/pull/5) |
| 5 | 정기 투표 | 완료 | [#6](https://github.com/MaTuna01/OhJumWhat/pull/6) |
| 6 | 배포·CI/CD | 완료 — https://www.ohjumwhat.cloud 운영 중 | [#7](https://github.com/MaTuna01/OhJumWhat/pull/7), 릴리스 [#8](https://github.com/MaTuna01/OhJumWhat/pull/8) |
| 7 | 마무리·QA | 진행 중 — 404·오류 화면, 링크 미리보기, 탭 제목, CSP 등(v1.1.0~v1.1.1), 데스크톱 레이아웃(v1.2.0) | [#10](https://github.com/MaTuna01/OhJumWhat/pull/10), [#14](https://github.com/MaTuna01/OhJumWhat/pull/14), [#16](https://github.com/MaTuna01/OhJumWhat/pull/16) |

## 계획을 세운 뒤 정한 것
- 메뉴를 추가해도 추가한 사람이 **자동으로 참여하지 않는다**. 추가와 참여는 따로 한다.
- Java는 **21 LTS**를 쓴다(개발 PC에 21이 설치되어 있어서). Spring Boot 4.1.1, PostgreSQL 18, Node 24.
- 디렉터리 이름은 IDE에서 구분하기 쉽게 `ohjumwhat-backend/`, `ohjumwhat-frontend/`로 한다. Java 패키지는 `com.ohjumwhat`이다.
- 세션은 **Spring Session JDBC로 DB에 저장**한다. 그래서 재시작·재배포 후에도 로그인이 유지되고, `SESSION` 쿠키는 30일 유효하다. 로그인하지 않은 요청에는 세션을 만들지 않는다(`NullRequestCache`).
- DB의 DATE·TIME 값이 시간대 변환으로 틀어지는 문제가 있어 `hibernate.jdbc.time_zone` 설정을 제거했다(5단계에서 발견). 이제 한국 기준 값이 그대로 저장된다.
- 백엔드 로깅은 Lombok `@Slf4j`로 한다. 로그에는 개인정보 대신 ID를 남긴다.
- 화면·디자인 시스템은 Figma(https://www.figma.com/design/w0OIV1khSVnxlf5KfRo6aP/OhJumWhat)가 기준이다. 글꼴은 Noto Sans KR이고, 색은 의미 기반 토큰(`bg-bg-*`, `text-text-*`, `border-border-*`)만 쓴다. 자세한 규칙은 CLAUDE.md 「디자인 시스템」에 있다.

## 형상관리
- 원격 저장소: https://github.com/MaTuna01/OhJumWhat
- 브랜치 흐름: `feature/<기능명>` → PR → `dev` → (사용자 결정) → `main`
  - 기능 브랜치는 `dev`의 최신 상태에서 분기한다.
  - 테스트·빌드를 통과하고 동작 확인을 마친 PR은 Claude가 `dev`에 머지한다(merge commit).
  - `dev` → `main` 승격은 사용자가 결정한다. 6단계부터는 `main`에 push하면 자동으로 배포된다.
- git에 올리지 않는 파일: `.env`, `ohjumwhat-backend/src/main/resources/application.yml`, 구글 OAuth 클라이언트 JSON(`client_secret_*.json`), 서버 pem 키. 저장소에는 예시 파일(`.env.example`, `application.example.yml`)만 커밋한다. 실제 값은 Notion 「개발 필요 파일」 페이지에 있다.

## 저장소 구조
```
ohjumwhat/
├─ ohjumwhat-backend/        Spring Boot 4 (Gradle Kotlin DSL, Java 21)
├─ ohjumwhat-frontend/       Vite + React 19 + TS + React Router + TanStack Query + Tailwind v4
├─ docs/PLAN.md              이 문서
├─ docker-compose.dev.yml    로컬 PostgreSQL
├─ .env.example              (.env는 gitignore)
├─ .claude/launch.json       Claude 미리보기용 프론트 dev 서버 설정
├─ CLAUDE.md / README.md
└─ (6단계) Dockerfile, deploy/, .github/workflows/
```

## 백엔드 설계
**패키지**
- `auth`: 보안 설정, 구글 로그인, `LoginUser`
- `user`: 사용자, `/api/me`
- `organization`: 조직, 멤버십, 초대
- `poll`, `menu`, `vote`: 투표, 메뉴 항목, 참여
- `schedule`: 정기 투표 규칙
- `common`: `Clock`, 공통 예외 처리, SPA 포워딩

**스키마**(Flyway가 원본, JPA는 `ddl-auto: validate`)
- V1: ERD의 테이블 7개
- V2: `spring_session` 테이블
- UNIQUE: memberships(org, user), votes(poll, user), polls(schedule_id, poll_date), menu_options(poll_id, name)
- CHECK: close_time > open_time, closes_at > opens_at. 인덱스: polls(organization_id, poll_date)
- FK
  - organization_id를 참조하는 FK와 polls를 참조하는 FK는 모두 `ON DELETE CASCADE`다.
  - polls.schedule_id는 `SET NULL`이다.
  - votes.option_id는 **`NO ACTION`**이다. RESTRICT로 두면 poll이 CASCADE로 지워질 때 즉시 검사에 걸리므로, 문장 끝에 검사하는 NO ACTION을 쓴다. 메뉴 삭제 가능 여부는 서비스에서 먼저 확인한다.

**시간**: 모든 계산은 `Clock` 빈(Asia/Seoul)으로 한다. "오늘"(`poll_date`), 마감 판정, 정기 투표 시각이 여기에 해당한다. 서비스 코드에서 `now()`를 직접 부르지 않는다.

**인증**(구현 완료)
- 구글 OIDC로 로그인하고, google_sub 기준으로 users를 upsert한다. 세션 principal은 `LoginUser`(users.id 포함)다.
- 로그인하지 않은 `/api/**` 요청에는 401을 준다. CSRF는 `csrf.spa()` 방식이고, `CsrfCookieFilter`가 매 응답에 `XSRF-TOKEN` 쿠키를 내린다.
- 로그인 후에는 항상 `/`로 보내고, 이동할 곳은 프론트가 정한다. 로그아웃은 `POST /logout`(204)이다.
- 파일이 없는 화면 경로는 `index.html`로 돌려준다(`SpaWebConfig`).

**API 규칙**(구현 완료)
- 사용자에게 보여줄 오류는 `ApiException`으로 던지고, `{"message": "..."}`로 응답한다.
- 조직 하위 API는 `MembershipService.requireMember`로 먼저 확인한다. 멤버가 아니면 404다.

**REST API**

| 영역 | 엔드포인트 | 상태 |
|---|---|---|
| 나 | `GET /api/me` (프로필 + lastVisitedOrgId), `GET /api/me/orgs` (이름·멤버 수·오늘 진행 중인 투표 여부) | 완료 |
| 조직 | `POST /api/orgs`, `GET /api/orgs/{id}` (last_visited_at 갱신), `PATCH /api/orgs/{id}`, `GET /api/orgs/{id}/members`, `DELETE /api/orgs/{id}/membership` | 완료 |
| 초대 | `GET /api/invites/{token}`, `POST /api/invites/{token}/join` | 완료 |
| 투표 | `GET /api/orgs/{id}/polls/today`, `POST /api/orgs/{id}/polls` (title, closesAt "HH:mm"), `GET /api/orgs/{id}/polls/{pollId}` (상세 집계) | 완료 |
| 메뉴 | `POST /api/polls/{pollId}/options`, `DELETE /api/polls/{pollId}/options/{optionId}`, `GET /api/orgs/{id}/menu-names?q=` (자동완성) | 완료 |
| 참여 | `PUT /api/polls/{pollId}/vote` `{optionId: number \| null}` (null이면 "오늘은 패스") | 완료 |
| 정기 | `GET/POST /api/orgs/{id}/schedules`, `PUT/DELETE /api/orgs/{id}/schedules/{sid}` | 완료 |

**핵심 규칙**
- **투표 상세 응답**(폴링 대상): 한 번 호출로 화면 전체를 그릴 수 있게 한다. 담는 값은 `status`(OPEN/CLOSED, now ≥ closesAt이면 CLOSED), `options[{id, name, createdBy, voters[], deletable}]`, `myVote`, `passed[]`, `nonRespondents[]`, `soloOptionIds[]`다.
- **참여 변경**: votes(poll, user)를 upsert하고 option_id만 바꾼다. 마감된 투표면 409, 다른 투표의 option이면 400이다.
- **메뉴 추가**: 앞뒤 공백을 지우고, 빈 값은 거부한다. 같은 이름이 있거나 마감된 투표면 409다. 자동 참여는 하지 않는다.
- **메뉴 삭제**: 추가한 사람만, 참여자가 0명이고 투표가 진행 중일 때 할 수 있다. 조건이 안 맞으면 403 또는 409다.
- **수동 투표 생성**: opens_at=now, poll_date=오늘(KST)로 만든다. closesAt은 now보다 뒤여야 한다.
- **탈퇴**(구현 완료): 조직 행을 비관적 락으로 잡는다. 진행 중인 투표의 내 votes를 지우고 membership을 지운다. 남은 멤버가 0명이면 조직을 삭제한다(나머지는 CASCADE).
- **정기 투표 스케줄러**: `@Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")`로 1분마다 돈다. 오늘 요일 비트가 켜져 있고 `open_time ≤ now < close_time`인데 (schedule, today)로 만든 poll이 없으면 새로 만든다(제목은 규칙 이름). UNIQUE 위반은 무시한다. 그래서 중복 생성이 막히고, 재시작으로 놓친 투표도 다음 실행 때 만들어진다.

## 프론트엔드 설계
- **라우트**
  - `/login`, `/invite/:token`, `/me`, `/`(진입 분기)
  - `/orgs/:orgId`(`OrgLayout` 아래: 홈, `polls/:pollId`, `schedules`, `settings`)
- **진입 분기**: 기억해 둔 경로(초대 링크) → 최근 조직 → `/me`. 로그인이 필요한 화면은 `RequireAuth`로 감싼다.
- **API**: `lib/api.ts`의 `api()`로만 호출한다(XSRF 헤더, 에러 메시지). 서버 상태 훅과 query key는 `src/queries/`에 둔다.
- **폴링**(4단계): `usePollDetail`은 OPEN이면 `refetchInterval: 3000`, CLOSED면 멈춘다. 백그라운드 탭에서는 폴링하지 않는다. 참여 클릭은 낙관적 업데이트로 처리한다.
- **공통 UI**(구현 완료)
  - 상단 바: 서비스명, 조직 전환 드롭다운, 프로필 메뉴
  - `Modal`(네이티브 dialog), `ConfirmDialog`, `InviteLinkField`, `lib/ui.ts`
- **남은 컴포넌트**
  - 4단계: `PollCard`(남은 시간/마감·인원·내 선택), `OptionRow`(인원·명단·내 선택 강조·삭제), `MenuInput`(debounce 자동완성), `NonRespondents`, `CreatePollModal`
  - 5단계: `ScheduleModal`(요일 체크박스와 비트마스크 변환)
- **남은 유틸**: `daysOfWeek.ts`(비트마스크와 요일 배열 변환, "평일" 표기), `time.ts`(남은 시간 표시)

## 남은 단계 상세
4. **투표 핵심** (`feature/poll`)
   - 수동 투표 생성, 오늘 열린 투표 목록(조직 홈 카드)
   - 메뉴 추가·삭제·자동완성
   - 참여·변경·"오늘은 패스"
   - 상세 집계 API, 투표 상세 화면(진행/결과 모드, 3초 폴링, 미응답자와 1인 메뉴 표시)
   - 여러 사람의 투표는 로컬 DB에 테스트 사용자를 넣어 확인한다.
5. **정기 투표** (`feature/schedule`): 스케줄 CRUD, 1분 주기 스케줄러, 정기 투표 관리 화면. 스케줄러 테스트에는 시각을 바꿀 수 있는 테스트용 Clock을 쓴다.
6. **배포·CI/CD** (`feature/deploy`)
   - Dockerfile(멀티스테이지: 프론트 빌드 → `static/`에 복사 → bootJar → JRE)
   - 운영 compose(Caddy + app + PostgreSQL 18, 데이터 볼륨은 `/var/lib/postgresql`), Caddyfile, 매일 pg_dump 백업
   - GitHub Actions: test → build → GHCR push → SSH로 `docker compose pull && up -d`
   - *사용자 준비*
     - 도메인
     - 가비아 서버(SSH 키 인증만 허용, 22·80·443만 개방)
     - GitHub Secrets(호스트, SSH 키)
     - 서버 `.env`
     - Google Console의 운영 리디렉션 URI(`https://<도메인>/login/oauth2/code/google`)
7. **마무리·QA**: 빈 상태·에러 메시지, 모바일 폭 레이아웃, 경계 케이스 점검

## 테스트 전략
- **백엔드**: 통합 테스트(`IntegrationTest` 상속, Testcontainers PostgreSQL, 테스트마다 TRUNCATE)
  - 로그인은 `TestAuth.loginAs`, CSRF는 `TestAuth.xsrf`로 만든다.
  - 남은 항목: 참여 upsert·변경, 마감 후 거부, 다른 투표의 option 거부, 메뉴 삭제 조건, 중복 메뉴 409, 스케줄 삭제 시 SET NULL, 스케줄러의 요일·시간 경계와 멱등성
- **프론트**: Vitest로 유틸과 진입 분기 로직을 테스트한다.
- CI(6단계): `./gradlew test`, `npm run build`, `npm run lint`, `npm test`

## 검증 (end-to-end)
1. 실행: `docker compose -f docker-compose.dev.yml up -d` → `./gradlew bootRun` → `npm run dev` → http://localhost:5173
2. 구글 로그인 → 조직 생성 → 초대 링크 참여 (완료)
3. 투표 생성 → 메뉴 추가·자동완성 → 참여·변경·패스. 3초 안에 반영되는지, 미응답자와 1인 메뉴가 표시되는지 확인한다.
4. 마감 시간이 지나면 결과 모드로 바뀌고 수정이 막히는지 확인한다.
5. 현재 시각+1분을 오픈 시간으로 하는 정기 투표를 만들고 자동 생성되는지 확인한다.
6. 마지막 멤버가 탈퇴하면 조직이 삭제되는지 확인한다 (완료).
7. 배포 후 운영 도메인에서 HTTPS·로그인·폴링을 스모크 테스트한다.
