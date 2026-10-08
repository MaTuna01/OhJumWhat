# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

**오점왓(ohjumwhat)**은 조직 안에서 점심·저녁 메뉴를 투표로 정하는 웹 서비스다. 결과는 "1등 메뉴"가 아니라 **메뉴별 참여자 명단(팀)**이다.

- 기획서(요구사항·화면 7개·ERD·삭제 규칙의 원본): Notion 「점심메뉴 선정」 https://app.notion.com/p/3eb11d838f8d802e81dbfcd702c34692
  기획서에는 서비스명이 가칭 "밥팟"으로 적혀 있지만, 정식 이름은 **오점왓**이다. 코드·설정·화면에서는 오점왓/ohjumwhat을 쓴다.
- 이후 PM을 영입해 협업하기 위해 기획서를 https://app.notion.com/p/92bf482da589416c88ce1307aa8be911?source=copy_link 로 옮겨 진행한다.
- 구현 계획·진행 현황·남은 단계는 [docs/PLAN.md](docs/PLAN.md)에 있다. 단계를 마치면 이 문서의 진행 현황도 갱신한다.
- 진행 단계는 같은 페이지의 Tasks DB에 7단계로 등록되어 있다. 단계를 마치면 해당 작업의 상태를 갱신한다.
- git에 올리지 않는 파일(`.env`, `application.yml`, OAuth JSON, 서버 pem 키)의 실제 내용은 Notion 「개발 필요 파일」 페이지(기획서 하위)에 있다.
- 원격 저장소: https://github.com/MaTuna01/OhJumWhat (`main`). 이슈·브랜치·커밋·PR 규칙은 아래 「작업 규칙」을 따른다.
- **보고는 한국어로 한다.** 사용자에게 하는 보고·설명·질문·요약과 커밋 메시지·이슈·PR을 모두 한국어로 쓴다.

## 작업 규칙 (이슈 → 브랜치 → 커밋 → PR)

작업은 모두 GitHub 이슈에서 시작한다. 이슈 번호로 커밋·PR을 이슈와 잇는다.

1. **착수 전에 이슈를 만든다.** 코드·문서를 고치기 전에 이슈를 만들고, 착수할 기능과 내용을 적는다(양식: `.github/ISSUE_TEMPLATE/task.md`, 작업 종류·내용·할 일·참고).
   - 제목은 `[종류] 요약`이다(예: `[feat] 근처 식당 찾기`, `[fix] 운영에서 지도 타일이 막힘`). 라벨은 feat → `enhancement`, fix → `bug`, docs → `documentation`을 붙인다.
   - `gh issue create --title "[feat] …" --body-file <본문> --label enhancement`
   - 하다가 범위가 바뀌면 이슈 본문의 할 일을 고치거나 댓글로 남긴다.
2. **브랜치 이름은 작업 종류로 시작한다.** `dev`의 최신 상태에서 `<종류>/<이름>`으로 분기한다(예: `feat/place-search`, `fix/csp-pstatic`). 이슈 번호는 브랜치 이름에 넣지 않고 커밋·PR에 적는다. `main`·`dev`에 바로 푸시하지 않는다.

   | 종류 | 쓰는 곳 |
   |---|---|
   | `feat` | 새 기능·화면 |
   | `fix` | 버그 수정 |
   | `refactor` | 동작을 바꾸지 않는 코드 정리 |
   | `docs` | 문서만(`CLAUDE.md`, `docs/`, README) |
   | `test` | 테스트만 |
   | `chore` | 빌드·설정·의존성·CI·배포 스크립트 |
   | `release` | 릴리스 준비(버전·CHANGELOG·업데이트 글) |

3. **커밋 메시지 첫 줄에 이슈 번호를 꼭 적는다.** 형식은 `[#이슈 번호] - 요약`이다(예: `[#2] - 기능개발`, `[#12] - 근처 식당 찾기 API 추가`). 본문에는 무엇을 왜 바꿨는지 적는다. 이슈 번호를 붙일 수 없는 작업이면 이슈부터 만든다.
4. **PR은 `dev`로 연다**(`gh pr create --base dev`). 제목은 커밋과 같은 `[#이슈 번호] - 요약`이고, 본문 첫 줄에 `이슈: #이슈 번호`를 적는다.
   - `dev`에 머지하는 것은 반드시 검토 후 개발자가 직접 진행하며, 백엔드·프론트 테스트와 빌드를 통과하고 동작 확인을 마친 뒤 PR 체크리스트를 채우고 머지한다. 머지 커밋도 컨벤션을 따르게 제목을 정한다: `gh pr merge --merge --subject "[#이슈 번호] - 요약 (#PR 번호)"`.
   - 머지하면 이슈를 닫는다(`gh issue close <번호> --comment "#<PR 번호>로 dev에 머지"`). `dev`는 기본 브랜치가 아니라 `Closes #N`으로는 닫히지 않는다.
5. **`dev` → `main` 승격(릴리스)은 사용자가 결정한다.** 릴리스도 이슈(`[release] vX.Y.Z`)를 만들고 `release/vX.Y.Z` 브랜치에서 버전·CHANGELOG·업데이트 글을 준비한다. 승격 PR 제목은 `[#이슈 번호] - 릴리스 vX.Y.Z: 요약`이고, 본문에 포함한 이슈·PR을 적는다. 릴리스 이슈는 배포·태그까지 마치고 닫는다.
   - 운영 버그를 고치는 승격은 제목 앞에 `[hotfix]`를 붙이고(`[hotfix] [#이슈 번호] - 릴리스 v1.7.1: …`, 라벨 `hotfix`·`bug`), 사용자가 버그 수정으로 알 수 있게 업데이트 글도 적는다.
- 이 규칙은 이슈 #48부터 적용한다. 그 전 브랜치(`feature/…`)·커밋에는 이슈 번호가 없다.
- 작업을 완료한 이후엔 개발에 사용한 브랜치를 정리한다.

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
- 프로필 사진 폴더는 `ohjumwhat.photos.dir`(`PHOTOS_DIR`)다. 로컬은 비어 있으면 `ohjumwhat-backend/data/photos`(gitignore), 운영은 이미지의 `/data/photos`(Docker 볼륨 `photos`), 테스트는 `build/test-photos`(`IntegrationTest`가 테스트마다 비운다)다.
- 지도 키는 `ohjumwhat.maps`(`KAKAO_REST_KEY`, `NAVER_MAP_KEY_ID`)다. 비어 있으면 지도·거리를 끄고 링크 방식만 쓴다(테스트·로컬에서 키 없이도 뜬다). 네이버 지도 키는 프론트 빌드에 넣지 않고 `GET /api/config`로 받는다.
- 웹 푸시 키는 `ohjumwhat.push`다: 공개 값 `FIREBASE_API_KEY`·`FIREBASE_PROJECT_ID`·`FIREBASE_APP_ID`·`FIREBASE_MESSAGING_SENDER_ID`·`FIREBASE_VAPID_KEY`와 비밀 값 `FIREBASE_SERVICE_ACCOUNT_BASE64`(서비스 계정 JSON을 base64 한 줄로)다. 하나라도 비거나 잘못되면(서비스 계정의 project_id가 다른 경우 포함) 푸시만 끄고 서버는 그대로 뜬다. 공개 값은 `GET /api/config`의 `push`로 주고, 꺼져 있으면 null이다. 준비 순서는 `docs/DEPLOY.md` 「5-2. Firebase 푸시」에 있다. 서비스 계정 JSON 파일은 저장소에 두지 않는다(`.gitignore`).
- actuator(헬스·지표)는 관리 포트 `management.server.port`(8081)에서만 연다(아래 「배포」의 모니터링). 로컬에서 앱을 두 개 띄우면 두 번째 앱은 `SERVER_PORT`와 함께 `MANAGEMENT_SERVER_PORT`도 바꾼다(아니면 8081이 겹쳐 뜨지 않는다). 공개 헬스 체크는 앱 포트의 `/healthz`다.

## 아키텍처

7단계(마무리·QA)까지 모두 마쳤고, https://www.ohjumwhat.cloud 에서 운영 중이다(`main` push 시 자동 배포, 버전은 `CHANGELOG.md`). 구현된 범위는 골격·스키마, 구글 로그인·세션, 조직, 투표·메뉴·참여·결과, 정기 투표, 배포, 마무리·QA(오류 화면, 링크 미리보기, 보안 헤더, 데스크톱 레이아웃)이고, 그 뒤에 서비스 관리자 콘솔(`/admin`)과 새 소식(`/notices`, 업데이트·개발자 노트)을 추가했다.

**동일 출처 구조.** 운영에서는 React 빌드 결과를 Spring Boot jar의 static 리소스로 넣어 이미지 하나로 배포한다(Caddy가 앞단). 개발에서는 Vite가 백엔드 경로를 프록시하는데, `changeOrigin: false`로 Host 헤더를 유지하고 백엔드는 `server.forward-headers-strategy: framework`로 설정한다. 그래서 CORS 설정이 없고, 인증은 JWT 없이 **세션 쿠키**로 한다. 프론트의 `/login`은 SPA 화면이고, Spring의 기본 로그인 페이지는 쓰지 않는다. 운영에서 파일이 없는 화면 경로는 `common/SpaWebConfig`가 `index.html`로 돌려준다.

**소개 페이지와 검색**(이슈 #122). 검색에 나오는 페이지는 로그인하지 않은 방문자가 `/`에서 보는 소개 페이지 하나다.
- `/`는 `common/RootPageController`가 로그인 여부로 고른다: 로그인했으면 앱(`index.html` → `RootRedirect`), 안 했으면 소개 페이지(`landing.html`). 같은 주소가 다른 파일이 되므로 `no-store`·`Vary: Cookie`로 캐시하지 않는다(소개 페이지를 304로 다시 써서 로그인 뒤에도 앱이 안 뜨는 일을 막는다). 소개 페이지 파일이 없으면(프론트를 넣지 않은 로컬 `bootRun`) 앱을 준다.
- 소개 페이지 본문은 `src/landing/LandingPage.tsx`(Figma `00-W`·`D00-W`)이고, `vite.config.ts`의 `landingPage` 플러그인이 빌드·개발 서버에서 `runnerImport`로 HTML을 그려 `landing.html`의 `<!--landing-->`에 넣는다(Vite 멀티 페이지, 입력 `index`·`landing`). 검색 로봇이 JS 없이 읽게 하려는 것이라 브라우저에서 React를 붙이지 않는다(상태·이벤트 없이 링크만). 개발 서버에서는 `/landing.html`로 본다.
- 앱 화면(`index.html`)은 로그인 화면이거나 로그인한 사람만 보는 화면이라 `noindex`다. 소개 페이지에만 canonical(`https://www.ohjumwhat.cloud/`)·검색용 제목·설명이 있다. `public/robots.txt`는 `/api/`·OAuth 경로만 막고(앱 화면을 막으면 noindex를 못 읽는다), `public/sitemap.xml`에는 `/` 하나만 둔다.
- 로그인 화면과 소개 페이지는 구글 로그인 버튼(`components/GoogleSignInLink.tsx`)과 투표 미리보기(`components/PollPreview.tsx`)를 함께 쓴다.

**인증 흐름**(`auth/SecurityConfig`)
- `/api/**`만 인증이 필요하다. 로그인하지 않았으면 리다이렉트 대신 401을 준다.
- 로그인 버튼은 `/oauth2/authorization/google`로 이동한다. 로그인에 성공하면 서버는 항상 `/`로 보낸다.
- 그다음 어디로 갈지는 프론트 `RootRedirect`가 정한다. `lib/entry.ts` 순서대로 sessionStorage에 기억해 둔 경로(초대 링크) → 최근 조직 → `/me`로 보낸다.
- `GoogleOidcUserService`가 google_sub 기준으로 users를 upsert하고, 세션 principal로 `LoginUser`(users.id 포함)를 둔다. 컨트롤러에서는 `@AuthenticationPrincipal LoginUser`로 받는다.
- 화면에 보이는 사람 이름은 **별명(`users.nickname`, V5), 없으면 구글 이름(`users.name`)**이다(`User.getDisplayName()`, JPQL은 `coalesce(u.nickname, u.name)`). 구글 이름은 로그인 때마다 갱신되고 별명은 그대로 둔다. 별명은 마이페이지 「프로필 수정」(`PUT /api/me/nickname`, 20자, 비우면 구글 이름)으로 정한다. `nickname`도 아래 `photo_key`처럼 엔티티에서 `insertable/updatable=false`이고 `UserRepository.updateNickname`으로만 바꾼다(같은 순간의 로그인이 옛 별명을 되써서 되돌리지 않게, #95). 사람 이름을 새로 내려주는 쿼리·응답을 만들 때도 이 규칙을 따른다(관리자 콘솔은 `googleName`도 함께 준다).
- 사진도 같은 규칙이다: **올린 사진(`users.photo_key`, V10), 없으면 구글 사진(`users.profile_image_url`)**. 응답의 `profileImageUrl`은 보여줄 사진이고, `User.getPhotoUrl()`·`User.photoUrl(key, googleUrl)`이 만든다. JPQL은 `u.photoKey, u.profileImageUrl`을 함께 고르고 DTO의 보조 생성자가 주소를 만든다(예: `MemberResponse`, `AdminResponses.UserRow`). 사람 사진을 새로 내려주는 쿼리도 이렇게 한다.
  - 사진 파일은 디스크(`ohjumwhat.photos.dir`)에 `{key}.jpg`(256px JPEG)로 두고, `GET /api/photos/{key}.jpg`(로그인 필요, `Cache-Control: private, immutable`)로 보낸다. 새로 올리면 키가 바뀐다.
  - 올리기는 `POST /api/me/photo`(multipart `photo`), 되돌리기는 `DELETE /api/me/photo`다. 브라우저가 512px로 잘라 보내고, 서버(`ProfilePhotoImages`)가 2048px 이하 JPEG·PNG만 받아 256px JPEG로 다시 그린다(EXIF 제거).
  - `photo_key`는 엔티티에서 `updatable=false`이고 `UserRepository.updatePhotoKey`로만 바꾼다. 로그인은 회원 행 전체를 다시 쓰므로, 그러지 않으면 같은 순간의 로그인이 옛 키를 되써서 사진이 깨진다.
  - 새 파일은 DB를 바꾸기 전에 쓰고, 옛 파일은 커밋한 뒤에 지운다(`ProfilePhotoStorage.deleteAfterCommit`). DB가 없는 파일을 가리키는 일은 없고, 실패하면 아무도 가리키지 않는 파일만 남는다.
- 프로필 소개는 **한줄 소개(`users.bio`, 50자)와 좋아하는 음식(`users.food_tags`, `VARCHAR(10)[]`, 직접 적는 태그 최대 3개)**다(V13, Notion 「17. 프로필 항목 추가」). 같은 조직 멤버가 프로필 모달에서 보고, 응답은 `MeResponse`·`MemberResponse`의 `bio`·`foodTags`(없으면 null·빈 배열)다.
  - `PUT /api/me/profile {bio, foodTags}`로 통째로 바꾼다(비우면 지운다). 정리 규칙은 `user/ProfileIntro`와 화면의 `lib/profile.ts`가 같다: 앞뒤·연속 공백 정리, 태그 앞의 `#` 제거, 제어 문자 거절, 글자(코드 포인트) 수, 띄어쓰기·대소문자만 다른 태그는 먼저 적은 것만 남긴다.
  - 두 컬럼도 `photo_key`처럼 엔티티에서 `insertable/updatable=false`이고 `UserRepository.updateIntro`로만 바꾼다(로그인이 옛 소개를 되써서 지우지 않게).
  - 가입 단계는 없다. 처음 가입하면 마이페이지로 가므로, 「내 정보」에 채우기 안내를 보여준다(상세 프로필이 비었으면 `03-N4`, 상세 프로필은 채웠고 소개만 비었으면 `03-N2`).
- 상세 프로필은 **MBTI·퍼스널컬러·취미·나이·직급**(`users.mbti`·`personal_color`·`hobbies VARCHAR(10)[]`·`age SMALLINT`·`job_title`, V15, Notion 「22. 프로필 항목 추가」)이다. 같은 조직 멤버가 프로필 모달에서 보고, 응답은 `MeResponse`·`MemberResponse`·관리자 회원 상세의 `details`(채우지 않았으면 null)다.
  - **다섯 항목 모두 필수**이고 본인은 지울 수 없다(관리자의 「상세 프로필 지우기」만 다섯 항목을 한꺼번에 비운다, 아래 「관리자 콘솔」). DB CHECK(`ck_users_profile_details`)가 「모두 비었거나 모두 채워졌거나」만 받는다. 「프로필 수정」 모달 안에 있어서, 다 채우기 전에는 사진·이름만 바꿔도 「저장」이 비활성이다(Figma `03-M2E`, 버튼 위에 남은 항목 안내). 관리자 「프로필 수정」은 비어 있던 회원이면 비운 채 저장할 수 있다.
  - `PUT /api/me/profile/details {mbti, personalColor, hobbies, age, jobTitle}`로 통째로 바꾼다(소개 `PUT /api/me/profile`과 따로). 규칙은 `user/ProfileDetails`와 화면의 `lib/profileDetails.ts`가 같다: MBTI는 E/I·S/N·T/F·J/P 4글자(대문자로 저장), 퍼스널컬러는 `PersonalColor` enum(봄 웜·여름 쿨·가을 웜·겨울 쿨, 기획서의 「봄 워터」는 오타로 봤다), 취미는 좋아하는 음식과 같은 태그 규칙(`ProfileText.tags` ↔ `lib/profile.ts addTags`)으로 1~5개·10자, 나이는 1~120 정수, 직급은 1~15자. 오류 문구는 기획서 그대로이고 화면 입력 순서대로 확인해 처음 걸린 항목의 문구로 답한다.
  - 다섯 컬럼도 엔티티에서 `insertable/updatable=false`이고 `UserRepository.updateDetails`로만 바꾼다(로그인이 옛 값을 되써서 지우지 않게).
  - 화면의 오류는 그 항목에서 포커스가 벗어난 뒤에만 보인다(MBTI·취미는 묶음 밖으로 벗어날 때). 취미는 아직 태그로 더하지 않은 글도 저장에 넣는다.
- CSRF는 `csrf.spa()` 방식이다. `CsrfCookieFilter`가 매 응답에 `XSRF-TOKEN` 쿠키를 내리고, 프론트 `lib/api.ts`가 GET이 아닌 요청에 `X-XSRF-TOKEN` 헤더로 붙인다.
- 로그아웃은 `POST /logout`이고 204를 준다.
- 세션은 Spring Session JDBC로 DB(`spring_session` 테이블, Flyway V2)에 저장한다. 그래서 서버를 재시작·재배포해도 로그인이 유지된다. `SESSION` 쿠키의 유효기간은 30일이다.
- request cache는 꺼 두었다(`NullRequestCache`). 로그인 후에는 항상 `/`로 가고, 로그인하지 않은 요청에는 세션을 만들지 않는다.

**API 규칙**
- 사용자에게 보여줄 오류는 `ApiException`(`notFound`/`badRequest`/`forbidden`/`conflict`)으로 던진다. `GlobalExceptionHandler`가 이를 `{"message": "..."}`로 응답하고, 요청 값 검증 실패(`@Valid`)와 파일 올리기 오류(multipart 아님·파트 없음 400, 한도 초과 413)도 같은 형식으로 준다. 프론트 `api()`는 이 `message`를 `ApiError.message`로 꺼낸다.
- 조직 하위 API는 먼저 `MembershipService.requireMember(orgId, userId)`를 호출한다. 멤버가 아니면 조직이 있는지도 알리지 않도록 404로 응답한다.
- 조회용 DTO가 필요하면 JPQL `select new ...Record(...)`로 바로 만든다(예: `MembershipRepository.findMembers`).
- 투표 관련 쓰기 API(메뉴 추가·삭제, 참여·패스·취소)는 모두 최신 `PollDetailResponse`를 돌려준다. 프론트는 이 응답을 바로 쿼리 캐시에 넣는다. 메뉴 댓글 쓰기 API만 그 메뉴의 최신 댓글 목록을 돌려준다(아래 「메뉴 댓글」).
- 투표 접근은 `PollService.getForMember`로 확인한다. 투표가 없거나 멤버가 아니면 404로 응답한다.
- 진행 중인 투표에서만 쓰기가 되고, 이 확인은 `PollService.requireOpen`이 한다. 마감되면 409다.
- 참여는 `VoteRepository.upsert`(native `ON CONFLICT`)로 한 사람 한 행을 유지한다.
- 동시 요청이 DB 제약에 걸리면 `DataIntegrityViolationException`이 발생하고, 409로 응답한다.

**백엔드 테스트**
- 통합 테스트는 `IntegrationTest`를 상속한다. 컨텍스트와 컨테이너를 공유하고, 테스트가 끝날 때마다 모든 테이블을 TRUNCATE한다.
- 로그인 상태는 `TestAuth.loginAs(user)`로 만든다.
- 시간에 따라 달라지는 동작은 `clock.set(2026, 9, 30, 11, 0)`(한국 시간)으로 시계를 고정해 테스트한다. 대상은 마감, 오늘, 정기 투표 등이다. `TestClock`은 `@Primary Clock`이고, 테스트가 끝나면 실제 시각으로 돌아간다.
- CSRF가 필요한 요청에는 `TestAuth.xsrf()`를 쓴다. Spring Security의 `csrf()` 헬퍼는 공유 CsrfFilter의 저장소를 세션 방식으로 바꿔 버려서, 이후 테스트에서 쿠키 발급이 깨진다. 그래서 쓰지 않는다.
- 웹 푸시는 `FakePushSenderConfiguration`이 대신한다: `@Primary` 가짜 발송기(보낸 기록, stale로 답할 FID 지정)와 `QueuedPushDispatcher`(큐에 모았다가 `drain()`으로 실행, 동기로 돌리면 끝난 트랜잭션에 섞인다)다. 기기 등록에는 실제 로그인 세션(`spring_session` 행)이 필요한데 `loginAs`만으로는 생기지 않으므로 `IntegrationTest.loginSession(user)`·`registerPushDevice(...)`와 `TestAuth.xsrf(sessionId)`를 쓴다.

**스키마는 Flyway가 소유한다.** `db/migration/V*.sql`이 원본이고 JPA는 `ddl-auto: validate`로 검증만 한다. 스키마를 바꿀 때는 기존 마이그레이션을 고치지 말고 새 `V{n}__*.sql`을 추가한 뒤 엔티티를 맞춘다. 컨텍스트 로드 테스트(`OhjumwhatApplicationTests`)가 불일치를 잡아낸다.

**엔티티는 연관관계 매핑 없이 FK를 `Long` ID 필드로 들고 있다**(`organizationId`, `pollId` 등). 조회는 JPQL/쿼리로 조합하고, `open-in-view: false`이므로 지연 로딩에 기대지 않는다. Java 패키지는 도메인별이다(`user`, `organization`, `poll`, `schedule`, `menu`, `vote`, `ranking`, `notice`, `place`, `chat`, `letter`, `guestbook`, `push`, `admin`, `common`).

**시간은 전부 KST 기준 `Clock` 빈으로 계산한다**(`common/TimeConfig`, `TimeConfig.KST`). "오늘"(`poll_date`), 마감 판정, 정기 투표 시각이 모두 여기에 해당한다. 서비스 코드에서 `LocalDate.now()`나 `Instant.now()`를 직접 부르지 않고 `Clock`을 주입받는다(테스트에서 고정 Clock으로 바꾸기 위함). DB 타임스탬프는 `timestamptz`와 `Instant`로 다룬다.
  - `poll_date`(DATE)와 `open_time`/`close_time`(TIME)은 한국 기준 값을 변환 없이 그대로 저장한다.
  - **`hibernate.jdbc.time_zone`을 설정하지 않는다.** 설정하면 `LocalTime`·`LocalDate`가 JVM 시간대(KST)에서 변환되어 저장된다. 예를 들어 08:00이 23:00으로, 날짜가 하루 전으로 바뀐다.
  - `common/TimeStorageTest`가 DB에 저장된 원본 값을 확인해 이 실수를 막는다.

**도메인 규칙 중 코드만 봐서는 알기 어려운 것**
- 마감은 별도 배치 없이 판정한다. 요청 시점이 `closes_at` 이후면 마감이다(`Poll.isClosed`).
- 진행 중인 투표는 조직 멤버 누구나 관리한다(투표 상세 제목 옆 ⋯, `PollService.update`/`close`/`delete`). 마감된 투표는 기록이라 수정·삭제하지 않는다.
  - 수정: 제목과 마감 시간("HH:mm", 투표 날짜 기준). 마감 시간은 지금보다 뒤여야 한다. 정기 투표도 그 투표만 바뀐다.
  - 지금 마감: 상태 컬럼 없이 `closes_at`을 지금으로 당긴다. CHECK `closes_at > opens_at` 때문에 열린 지 1초 안에는 409다.
  - 삭제: 수동 투표만 된다. 정기 투표로 열린 투표는 지워도 스케줄러가 1분 안에 다시 열기 때문에 409를 주고 "지금 마감"을 안내한다.
- 정기 투표는 매분 0초(KST)에 `PollScheduler`가 `ScheduledPollOpener.openDuePolls()`를 호출해서 연다.
  - 조건: 오늘 요일이 규칙에 포함되고, 오픈 ≤ 지금 < 마감이고, (규칙, 오늘) 투표가 아직 없을 때
  - 투표의 `opens_at`은 규칙의 오픈 시각이고, 제목은 규칙 이름이다.
  - `polls(schedule_id, poll_date)` UNIQUE 제약이 중복을 막고, 제약 위반은 무시하는 것이 의도된 동작이다. 서버가 오픈 시각에 꺼져 있었어도 마감 전에 켜지면 그날 투표가 열린다.
  - 규칙을 수정하면 앞으로 열릴 투표부터 적용된다. 규칙을 삭제해도 열린 투표는 남는다.
  - 테스트에서는 `ohjumwhat.scheduler.enabled=false`로 백그라운드 실행을 끄고, `ScheduledPollOpener`를 고정된 `TestClock`으로 직접 호출한다.
- `poll_schedules.days_of_week`는 요일 비트마스크다(월=1 … 일=64, 평일=31). 변환은 `PollSchedule.bitOf`/`runsOn`으로 한다.
- `votes`는 (poll, user)당 한 행이다. 메뉴를 바꾸면 `option_id`만 갱신한다. `option_id`가 NULL이면 "오늘은 패스"다.
  - 행이 없으면 미응답이다. 진행 중에는 `DELETE /api/polls/{pollId}/vote`로 응답을 취소해 처음처럼 미응답으로 돌아간다(행을 지운다, 응답이 없어도 같은 응답을 준다). 화면에서는 고른 메뉴 카드나 「✓ 오늘은 패스했어요」를 다시 누르면 취소한다(확인 창 없이 바로).
- `votes.option_id` FK는 의도적으로 `NO ACTION`이다(RESTRICT 아님). 투표를 CASCADE로 삭제할 때 검사가 문장 끝으로 미뤄지게 하기 위해서다. 참여자가 있는 메뉴를 삭제하지 못하게 막는 검사는 서비스에서 먼저 한다.
- 메뉴를 추가해도 추가한 사람이 자동으로 참여하지 않는다. 메뉴는 추가한 사람만, 참여자가 0명이고 투표가 진행 중일 때만 삭제할 수 있다. 메뉴 이름은 trim해서 저장하고, (poll, name)은 UNIQUE다.
- 메뉴의 식당(`menu_options.link_url`·`place_name`·`place_address`·`kakao_place_id`·`place_query`, V4·V7·V8·V9)은 선택이다. 메뉴를 추가할 때 붙이거나, 추가한 사람이 진행 중에 `PUT /api/polls/{pollId}/options/{optionId}/link {link, placeName, placeAddress, kakaoPlaceId, placeQuery}`로 달고 고친다. 지도 링크(이름·주소) **또는** 근처 식당 찾기로 고른 카카오 식당(장소 ID·검색어) 중 하나만 받고(둘 다 오면 400), 둘 다 비면 식당을 뺀다. 화면은 새 탭(`rel="noopener noreferrer"`)으로 연다(Notion 「11. 식당 정보·네이버 지도 연동」, 「15. 식당 검색·네이버 지도 연동」).
  - 링크 규칙은 `place/PlaceLinks`다. 지도 서비스를 가리지 않고 http/https 주소면 받는다. 공유 문구에서 네이버 지도 링크를 먼저 꺼내고(스킴이 없어도 된다), 장소 ID가 있는 네이버 링크는 정식 링크 `https://map.naver.com/p/entry/place/{id}`로 바꾼다. 길이(500자)는 바꾼 뒤에 검사한다.
  - naver.me 단축 링크는 `place/PlaceLinkResolver`가 첫 리디렉션만 읽어 장소 ID로 정식 링크를 만든다(`JdkNaverShortLinks`: 검증한 코드로 `https://naver.me/{code}`를 직접 만들고, 리디렉션은 따라가지 않고, 2·3초 타임아웃). 확인하지 못하면(시간 초과·네이버 오류) 단축 링크를 그대로 두고, naver.me가 없는 코드라고 답하면(404·410, `NaverShortLinks.NotFound`) 잘못 붙인 링크라 400이다. 네트워크를 쓰므로 **컨트롤러에서(트랜잭션 밖)** 확인하고, 서비스는 정리된 `PlaceLink`를 받는다. 테스트는 `FakeNaverShortLinksConfiguration`(고정 표)이 대신한다.
  - 식당 이름·주소는 사용자가 붙인 공유 글에서 프론트가 미리 채운 값이다(`lib/place.ts parseShareText`, 주소는 시·도 이름으로 시작하는 줄). 「네이버 지도에서 찾기」는 「조직 검색 지역 + 메뉴 이름」으로 네이버 지도 검색을 연다(`naverSearchUrl`).
- 조직 위치(`organizations.area`·`office_name`·`office_link_url`·`office_address`·`search_radius`, V7·V8)는 `PUT /api/orgs/{orgId}/location`으로 통째로 바꾼다(멤버 누구나, 빈 값은 지운다, 반경은 500·1000·2000m이고 없으면 1000). 검색 지역은 「네이버 지도에서 찾기」 검색어 앞에 붙는다. 조직 주소는 지도·거리의 기준점이라 저장 전에 카카오로 찾을 수 있는지 확인한다(못 찾으면 400, 카카오를 못 쓰면 확인 없이 저장). 장소 링크도 같은 링크 규칙을 쓴다. 화면에서는 「조직 위치·조직 주소·장소 이름」이라고 부르고 「회사」라고 쓰지 않는다(회사·학교·동아리 등 여러 조직이 쓴다). 코드·DB 이름은 `office_*` 그대로다.
- **지도: 찾기는 카카오 로컬, 보여주기는 네이버 지도.** 카카오 로컬 REST(`place/KakaoLocal` → `RestKakaoLocal`, 키는 서버에만)로 주소를 좌표로 바꾸고, 화면의 지도는 네이버 지도 JS(`lib/naverMaps.ts`, `components/NaverMap.tsx`)로 그린다. 네이버 지역 검색 API는 5건·위치 검색 없음·저장 불가라 쓰지 않는다.
  - **약관상 좌표·검색 결과는 저장·캐시하지 않는다.** 저장하는 것은 사용자가 입력한 주소 문자열과, 카카오 식당의 장소 ID·장소 링크(서버가 ID로 `https://place.map.kakao.com/{id}`를 만든다)·사용자가 친 검색어뿐이다(카카오 식당의 이름도 저장하지 않는다, 데브톡 9/17 답변 기준). 근처 식당 찾기는 `GET /api/orgs/{orgId}/places/search?q=`(조직 주소 기준 반경, 음식점 FD6, 카카오 3페이지 45개까지 한 번에)이고, 검색어가 있으면 **정확도순**으로 받아 이름·분류에 검색어가 있는 곳(`matched`)을 앞에 가까운 순으로, 메뉴·태그로만 걸린 곳을 뒤에 가까운 순으로 둔다(거리순만 쓰면 「떡볶이」에 메뉴에 떡볶이가 있는 치킨집이 맨 앞에 오고, 진짜 떡볶이집은 45개 밖으로 밀린다). 검색어가 비면 분류 검색으로 가까운 순 둘러보기다. 분류는 체인 이름을 뺀 가장 자세한 분류다(`KakaoPlace.category`). `GET /api/orgs/{orgId}/places?optionIds=`가 볼 때마다 조직·식당 주소를 좌표로 바꿔 준다(`place/PlaceSearchService`, 가상 스레드 병렬·4초 마감, 실패한 항목은 빼고 부분 결과). ref는 서버가 그 조직의 옵션에서 만든다(클라이언트가 임의 검색어로 쿼터를 쓰지 못하게). 멤버 확인을 카카오 호출보다 먼저 한다.
  - 카카오 식당은 `places`가 볼 때마다 고를 때와 같은 방법(검색어, 조직 좌표, 조직 반경, 순서)으로 다시 찾아 장소 ID가 같은 결과의 이름·분류·도로명 주소·좌표를 준다(검색어별로 묶어 페이지를 넘기며 찾으면 멈춘다). 못 찾으면(조직 반경이 바뀐 경우 등) 반경 20km를 가까운 순으로 한 번 더 본다. 조직 위치를 옮기면 못 찾을 수 있다(그때 카드는 저장한 카카오 링크로 「지도 · 카카오맵 ↗」). 카드·결과 복사의 링크는 다시 찾은 이름 + 도로명 주소로 만든 네이버 지도 검색 링크다(`lib/place.ts naverPlaceSearchUrl`).
  - 자동완성(`menu-names`)과 추천(`menu-recommendations`)은 같은 이름의 메뉴에 지난번 붙인 식당(`lastPlace`, 식당이 붙은 가장 최근 메뉴)을 준다. 고르면 그 식당까지 붙여 추가한다(직접 고른 식당이 있으면 그쪽). 카카오 식당의 이름은 `lastPlace.optionId`로 `places`에서 받는다.
  - 카카오 호출은 네트워크를 쓰므로 트랜잭션 밖(컨트롤러·비트랜잭션 서비스)에서 한다. 로그에는 주소·검색어를 남기지 않는다. 테스트는 `FakeKakaoLocalConfiguration`(고정 표, 호출 횟수)이 대신하고, `RestKakaoLocalTest`는 `MockRestServiceServer`로 요청을 확인한다.
  - 거리·도보 시간은 프론트가 좌표로 계산한다(`lib/distance.ts`: 직선거리, 도보 = 거리 × 1.3 ÷ 분당 67m, 60분이 넘으면 거리만). 투표 상세는 위치를 폴링과 따로 받는다(`queries/places.ts usePlaces`, 키 = 조직 주소 + 메뉴의 식당 서명). 지도는 `useMediaQuery`로 모바일(메뉴 목록 위, 진행 중에는 접힘)과 데스크톱(사이드 맨 위) 중 한 곳에만 그린다.
  - 지도 마커 글자는 사용자 입력(메뉴 이름)이라 DOM 노드의 `textContent`로만 넣는다. `NaverMap`은 `<dialog>`·접힌 영역에서 크기가 0일 수 있어 ResizeObserver로 크기가 생긴 뒤에 지도를 만든다.
- 조직 안에는 관리자가 없고 모든 멤버의 권한이 같다(서비스 전체를 관리하는 관리자 콘솔은 아래 별도). 마지막 멤버가 탈퇴하면 조직을 삭제하고, 하위 데이터는 DB `ON DELETE CASCADE`로 함께 지운다. 정기 규칙을 삭제하면 `polls.schedule_id`만 NULL이 된다.
- 멤버가 탈퇴하면 그 조직의 **진행 중인** 투표에서 그 사람의 votes만 지운다. 마감된 투표 기록은 남긴다.
- 지난 투표(`GET /api/orgs/{id}/polls/history?page=`)는 `poll_date`가 오늘(한국) 이전인 투표를 최신순으로 10개씩 준다. 오늘 투표는 `/polls/today`가 맡는다. 메뉴·응답은 페이지 단위로 한 번에 읽는다(`findByPollIdIn`).
  - 화면에서는 진행 중인 투표와 지난 투표를 날짜·모양으로 구분한다(Notion 「24. 현재 진행 중인 투표랑 지난 투표가 구분이 잘 안 되는 문제」, 이슈 #123, Figma `04-S`·`05b-D`). 조직 홈은 「진행 중인 투표」(브랜드 테두리 카드)와 「오늘 마감된 투표」(소제목 아래 흐린 카드)를 나누고, 지난 투표 줄은 날짜를 제목 앞에 두고 「마감」 배지를 붙인다. 마감된 투표 상세(관리자 포함)는 제목 위에 「오늘 마감된 투표」/「지난 투표 · 9월 28일 (일)」 줄을 둔다(날짜는 `opensAt`의 한국 날짜 = `poll_date`, `lib/pollStatus.ts`). 상태 배지는 `components/PollStatusBadge.tsx` 하나를 쓴다.
- 메뉴 댓글(`menu/MenuComment*`, V11 `menu_comments`, Notion 「14. 투표별 채팅/댓글 기능 추가」 1단계)은 채팅과 독립된 REST 기능이다(실시간 아님).
  - `GET/POST /api/polls/{pollId}/options/{optionId}/comments`, `PUT/DELETE …/{commentId}`. 멤버만(아니면 404), 쓰기·고치기·지우기는 **「마감 = 기록」 원칙대로 진행 중에만**(`requireOpen`, 마감 뒤 409) 하고, 고치기·지우기는 쓴 사람만(403). 마감된 투표의 댓글은 읽기만 한다. 쓰기 API는 그 메뉴의 최신 댓글 목록을 돌려준다.
  - 본문은 `common/UserText`로 정리한다(앞뒤 공백 제거, 제어 문자 거절, 200자는 글자(코드 포인트) 수). 고치면 `edited_at`이 생겨 「수정됨」으로 보인다.
  - 메뉴를 지우면 댓글도 CASCADE로 지워진다(메뉴 삭제 조건은 그대로). 강제 탈퇴로 회원이 지워지면 `user_id`만 NULL → 「탈퇴한 사용자」, 조직 탈퇴는 글을 남긴다.
  - 투표 상세 응답의 메뉴에 `commentCount`가 있어 진행 중에는 3초 폴링으로 개수만 바뀐다. 목록은 펼칠 때·창이 다시 보일 때·내가 쓸 때 받는다(`queries/comments.ts`, 쓰면 상세 캐시의 개수도 맞춘다).
  - 관리자는 `GET /api/admin/menu-options/{optionId}/comments`, `DELETE /api/admin/menu-comments/{commentId}`로 마감과 상관없이 지운다.
- 투표 채팅(`chat` 패키지, V12 `chat_messages`, Notion 「14. 투표별 채팅/댓글 기능 추가」 2단계): **보내기·고치기·지우기는 REST, 받기만 WebSocket**(STOMP 없음).
  - 기간: 투표 오픈 ~ **마감 + 1시간**(`Poll.getChatClosesAt`, 투표 상세 응답의 `chatClosesAt`). 마감처럼 요청 시각으로 판정하고 「지금 마감」·마감 시간 수정을 따라 움직인다. 닫힌 채팅은 읽기만 한다(지난 투표에서도 보인다).
  - REST: `GET /api/polls/{pollId}/messages?before=`(50개씩, 오래된 → 최신), `POST`, `PUT/DELETE …/{id}`. 멤버만(404), 쓰기는 채팅이 열려 있을 때만(409 「채팅이 닫혔어요.」), 고치기·지우기는 쓴 사람만(403). 300자(줄바꿈 허용, `UserText`), 한 사람 10초 10개(429, `ChatRateLimiter`, 서버 메모리). 지우면 행은 남기고 본문만 비운다(「삭제된 메시지예요」). 로그에 본문을 남기지 않는다.
  - WebSocket `/api/polls/*/ws`(`ChatSocketConfig`): `/api/**` 아래라 세션 쿠키 인증(아니면 401)을 그대로 받고, `ChatHandshakeInterceptor`가 멤버(404)·채팅 기간(409)·사용자·투표당 연결 3개(429)를 확인한다. 허용 출처는 기본값(같은 출처만, 프록시 뒤에서는 `X-Forwarded-*` 기준). 받기 전용이라 화면이 보내는 글은 무시한다.
  - `ChatHub`가 연결을 서버 메모리에 든다(앱이 하나라 브로커가 필요 없다). 쓰기 트랜잭션이 커밋된 뒤(`@TransactionalEventListener`) 같은 투표의 연결로만 `{"type":"created|updated|deleted","message":{…}}`를 보낸다. 메시지에 "내 글인지"는 없고 화면이 `author`로 판단한다.
  - 연결 끊기: 투표 삭제(`PollDeletedEvent`)·조직 탈퇴·멤버 제거·강제 탈퇴(`MembershipEndedEvent`)·조직 삭제(`OrganizationDeletedEvent`)는 커밋 후 이벤트로 바로, 로그아웃은 `SecurityConfig`의 `LogoutHandler`가 그 로그인(HTTP 세션)의 연결만 끊는다(4003). 30초마다 `ChatSweeper`가 닫힌 채팅(4001)·없어진 투표·멤버가 아닌 연결을 끊고 연결 확인 신호 `{"type":"ping"}`을 보낸다(브라우저 JS는 ping 프레임을 못 봐서 글로 보낸다). 테스트에서는 `ohjumwhat.scheduler.enabled=false`로 끄고 `ChatSweeper.sweep()`을 직접 부른다.
  - 화면(`lib/chatSocket.ts openChatSocket`, `hooks/usePollChatSocket.ts`): 채팅이 열려 있고 탭이 보일 때만 연결한다. 끊기면 1초부터 두 배씩(최대 30초) 다시 연결하고, 4001·4003이면 멈춘다. 75초 동안 아무것도 받지 못하면(반쯤 끊긴 연결) 버리고 다시 연결한다. 연결될 때마다 목록을 다시 받아 놓친 메시지를 채운다(`queries/chat.ts`, `structuralSharing`으로 그 순간의 캐시와 합쳐 WebSocket으로 받은 것을 잃지 않는다, 합치기 규칙은 `lib/chat.ts mergeMessages`).
  - 관리자: `GET /api/admin/polls/{pollId}/messages`, `DELETE /api/admin/chat-messages/{id}`(기간과 무관, 소프트 삭제 + 보고 있는 사람에게 전송).
  - **안 읽은 메시지**(V14 `chat_reads`, Notion 「18. 채팅에 메시지가 오면 알림」): 읽은 위치를 서버에 (투표, 사람)당 한 행으로 둔다(기기 간 동기화, 행이 없으면 0). 푸시 알림이 아니라 화면 안의 배지·미리보기·구분선으로 알린다.
    - 안 읽음 = 남이 쓴(탈퇴한 사용자 글 포함) 지우지 않은 메시지 중 읽은 위치보다 뒤. 서버 `ChatReadRepository.countUnread`와 화면 `lib/chat.ts unreadCount`가 같은 규칙이라 함께 고친다. 처음 여는 투표는 남의 메시지가 모두 안 읽음이다.
    - `PUT /api/polls/{pollId}/messages/read {lastReadId}`(204, 멤버만, 채팅이 닫힌 뒤에도 된다)는 네이티브 upsert로 뒤로 가지 않고 그 투표의 마지막 메시지를 넘지 않는다(메시지가 없으면 행을 만들지 않는다). 보내면 서버가 보낸 메시지까지 읽음으로 한다. 목록 응답에 `lastReadId`, 오늘 투표 요약에 `unreadMessages`·`chatClosesAt`(진행 중이거나 채팅이 열린 투표가 있으면 조직 홈이 15초마다 다시 받는다).
    - 읽음 판정(`hooks/useChatReading.ts`): 목록 끝의 sticky 표시(`ListEnd`)가 화면 안이고(IntersectionObserver) 탭이 보이고 목록이 맨 아래일 때만. 위로 올려 읽는 중에 온 메시지는 안 읽음으로 남아 「새 메시지 N ↓」가 뜬다. 보이기 시작한 순간의 안 읽은 범위에 「여기부터 새 메시지」를 고정하고(내가 쓰면 지운다), 보는 동안 온 메시지는 잠깐 강조한다.
    - 전송(`queries/chat.ts useMarkChatRead`): 캐시의 `lastReadId`는 바로 올리고(`mergeChatPage`가 큰 쪽으로 합친다), 서버에는 1초에 한 번 가장 뒤의 위치만, 떠날 때·탭을 숨길 때는 `keepalive`로 바로 보낸다. 성공하면 오늘 투표 카드를 다시 받는다. `setQueryData`는 같은 값도 `structuralSharing`(`mergeChatPage`)으로 새 객체를 만들어 다시 그리므로, 바뀌지 않으면 업데이터가 `undefined`를 돌려 캐시를 건드리지 않는다(아니면 읽음 effect가 끝없이 돈다).
    - 버튼(`hooks/useChatUnread.ts`): 모바일 「💬 채팅」(시트가 닫혀 있을 때)과 데스크톱 「💬 새 메시지」(채팅 카드가 화면 밖일 때)에 빨간 배지(99+, 최신 50개보다 앞부터 안 읽었으면 「50+」)를 두고, 남의 새 메시지가 오면 4초 미리보기를 띄운다(처음 받은 목록은 미리보기하지 않는다).
  - Spring 7은 SockJS 스케줄러를 `TaskScheduler` 빈으로 내놓지 않아 정기 투표 `@Scheduled`를 가로채지 않는다(`ChatIntegrationTest`가 고정). 실제 핸드셰이크(세션 쿠키·출처·프록시 헤더)는 `ChatSocketTest`(실제 포트)가 확인한다.
- 쪽지(`letter` 패키지, V16 `letters`·`letter_blocks`·`letter_reports`, 프론트 `/letters`, Notion 「21. 같은 조직에 추가된 사용자들끼리 쪽지 주고받기 기능」): 같은 조직 멤버끼리 한 통씩 주고받는다(채팅처럼 이어지는 대화가 아니다). 투표와 상관없고, 실시간 연결 없이 상단 바가 안 읽은 수를 30초마다·창으로 돌아올 때 받는다(`queries/letters.ts useUnreadLetters`). 코드 이름은 `NoticeKind.NOTE`(개발자 노트)와 헷갈리지 않게 `letter`다.
  - API: `GET /api/letters?box=received|sent&before=`(20통씩), `GET /api/letters/unread`, `POST /api/letters {organizationId, recipientId, body, anonymous}`, `POST /api/letters/{id}/reply {body, anonymous}`, `PUT …/{id}/read`, `DELETE …/{id}`(내 쪽에서만), `POST …/{id}/block`, `GET /api/letters/blocks`, `DELETE /api/letters/blocks/{id}`, `POST …/{id}/report {reason, block}`. 참·거짓 값은 `Boolean`으로 받는다(Jackson 3은 빠진 `boolean`을 오류로 본다).
  - 규칙: 보낸 사람·받는 사람 모두 그 조직 멤버여야 하고(아니면 404), 나에게는 못 보낸다(400). 본문 500자(`UserText`, 줄바꿈 허용), 한 사람이 10분에 10통(답장 포함, `LetterRateLimiter` → `common/SlidingWindowRateLimiter`, 채팅 `ChatRateLimiter`도 이것을 쓴다). 사용자는 행을 지우지 않고 쪽마다 소프트 삭제(`sender_deleted_at`·`recipient_deleted_at`)라 신고가 쪽지를 잃지 않는다. 로그에는 본문을 남기지 않는다.
  - **익명 보호**: 익명 쪽지도 `sender_id`를 저장하지만 받은 사람의 응답에는 보낸 사람의 id·이름·사진이 없다(`LetterRepository`가 사람 join 조건에서 빼서 아예 고르지 않는다, `canReply`·`reported`도 발신자 id 없이 계산). 익명 쪽지에 답장하면 그 답장의 `recipient_hidden`으로 답장한 사람에게도 받는 사람을 숨긴다. **답장의 익명 여부는 서버가 정한다**: 익명으로 시작한 사람이 다시 답할 때만 익명이고(받은 쪽지의 `replyAnonymous`, 화면은 체크를 잠가 보여준다), 그 밖의 답장은 실명이다. 답장은 원래 보낸 사람에게 가고 그 사람은 누구에게 보냈는지 알기 때문에 익명이 될 수 없다(답장 요청에는 `anonymous`가 없고, 화면은 체크 대신 안내를 보여준다). 답장할 수 없는 이유는 모두 「답장할 수 없는 쪽지예요.」이고, 상대가 조직을 떠났는지는 확인하지 않는다(조직이 있고, 내가 멤버이고, 보낸 사람 계정이 있으면 된다). 받은 익명 쪽지의 `canReply`는 보낸 사람 계정을 보지 않는다(강제 탈퇴와 함께 바뀌면 누가 보냈는지 짐작하게 한다, 답장하면 같은 409). 익명 쪽지는 로그에도 보낸 사람·받는 사람을 남기지 않는다.
  - **차단**은 익명 여부로 나눈다(V17). 실명 차단은 사람 단위(`uk_letter_blocks_named`)로 그 사람의 실명 쪽지를 숨기고, **익명 차단은 쪽지 한 통 단위**(`uk_letter_blocks_anonymous`)로 그 쪽지만 숨긴다(`LetterRepository.VISIBLE_RECEIVED`, 목록·안 읽은 수·한 통 조회가 공유). 익명 차단을 사람 단위로 두면 같은 사람의 다른 익명 쪽지까지 사라지고 차단 목록 줄 수도 늘지 않아 「같은 사람이 썼다」가 드러난다(#77). 새로 오는 쪽지는 보낼 때 버린다(`recipient_deleted_at`, 보낸 사람에게는 보낸 것으로 보인다): 익명 쪽지는 그 사람에게 걸린 차단이 하나라도 있으면, 실명 쪽지는 실명 차단이 있을 때만. 차단을 풀면 차단 전에 받은 쪽지만 돌아온다. 익명 차단 목록에는 이름 없이 차단한 쪽지의 첫 줄만 준다. 보낸 사람이 강제 탈퇴해도 차단은 남고(`blocked_user_id`만 NULL, 사라지면 차단한 익명 쪽지가 다시 보여 탈퇴가 드러난다), 탈퇴한 사람의 익명 쪽지도 차단할 수 있다(실명 쪽지는 화면에서 「차단」을 감춘다). 차단·신고는 다시 눌러도 그대로다(차단으로 숨긴 쪽지도 찾는다). 신고 사유는 한 줄이라 화면이 줄바꿈을 공백으로 합쳐 보낸다(`lib/letters.ts reportReason`).
  - **신고**: 받은 사람만, 한 쪽지는 한 번(사유 선택 100자, `block`이면 함께 차단). 관리자는 신고된 쪽지만 본다(`GET /api/admin/letter-reports?status=open|all`, 익명이어도 실제 보낸 사람, `POST …/{id}/resolve`, 개요 `Stats.openReportCount`). 강제 탈퇴하면 그 사람이 보낸 쪽지의 열린 신고를 처리 완료로 바꾼 뒤 회원을 지운다.
  - 지워질 때: 보낸 사람이 강제 탈퇴하면 `sender_id`만 NULL(「탈퇴한 사용자」, 답장 불가), 받은 사람이 강제 탈퇴해도 쪽지는 지우지 않고 `recipient_id`만 NULL(V18: 보낸 쪽지함에는 「탈퇴한 사용자」, 숨긴 상대는 계속 「익명」, 답장 원문과 신고도 남는다. 지우면 익명 상대가 방금 탈퇴했다는 것이 드러나고 신고 증거가 사라졌다), 조직이 없어지면 `organization_id`만 NULL(「삭제된 조직」). 조직을 떠나도 지난 쪽지는 남는다.
  - 화면: 상단 바 봉투(`LetterButton`, 빨간 배지 99+) → `/letters`(`LettersPage`, 탭 `?box=sent`, 보고 있는 동안 안 읽은 수가 늘면 받은 쪽지함을 다시 받는다). 쪽지 쓰기는 `AppLayout`의 `LetterComposerProvider`가 하나만 두고(`hooks/useLetterComposer.ts`의 `compose`·`notify`), 멤버 프로필의 「쪽지 보내기」(받는 사람 고정)·쪽지함의 「쪽지 쓰기」(조직·멤버 고르기)·「답장」이 연다. 받은 쪽지는 열면 바로 읽음으로 바꾸고 배지를 줄인다(`useMarkLetterRead`).
- 방명록(`guestbook` 패키지, V19 `guestbook_entries`·`guestbook_reports`, 이슈 #91, Notion 「20. 방명록 기능 추가」): 프로필에 남기는 한 줄 글이다. **조직이 아니라 사람에게 속한다**(사람마다 방명록 하나, 미니홈피식).
  - 볼 수 있는 사람은 주인(소속 조직이 없어도)과 주인과 조직을 하나라도 같이 쓰는 사람(`MembershipRepository.sharesOrganization`)이고, 그 밖에는 404다. 다른 조직 사람이 쓴 글과 그 사람의 이름·사진도 모두에게 보인다(사용자 결정).
  - API: `GET /api/guestbook/users/{ownerId}?page=`(10개씩 최신순, `{entries, page, totalPages, totalCount, owner, seenAt}`), `POST …/users/{ownerId} {body}`(201 + 쓴 사람이 보는 0쪽), `DELETE /api/guestbook/entries/{id}`, `POST …/entries/{id}/report {reason?}`, `GET /api/guestbook/alerts`, `POST /api/guestbook/seen {until}`, `POST /api/guestbook/warnings/ack {until}`.
  - 규칙: 실명(별명) 전용, 주인은 자기 방명록에 쓰지 않는다(400). 100자 한 줄(`UserText`, 줄바꿈 불가), 수정·댓글·답글 없음, 영구 보관. 도배 방지는 한 사람이 모든 방명록을 합쳐 5초에 한 번이고(`GuestbookRateLimiter` → `SlidingWindowRateLimiter(1, 5초)`, 429), 글을 확인한 뒤에 센다(오타로 5초를 쓰지 않게). 화면은 성공·429 뒤 앱 전체에서 하나인 카운트다운(`hooks/useGuestbookCooldown.ts`)을 보여준다.
  - 지우기는 쓴 사람(지금 같은 조직이 아니어도)과 주인이고 소프트 삭제(`deleted_at`)다. 지운 글은 목록·개수·새 글 수에서 빠지고, 신고 증거로 남는다.
  - 신고는 **주인만**, 글 하나당 한 번(`guestbook_reports.entry_id` UNIQUE, 사유 선택 100자, 신고자 컬럼 없음)이다. 관리자 「신고」 탭에서 「글 제한」(`restricted_at`, 지운 글도 제한한다)이면 응답에서 본문을 아예 고르지 않아 모두에게 「관리자에 의해 제한된 게시글입니다」로 보이고, 「문제 없음」이면 그대로 둔다. 다른 결과로 다시 처리하면 409다. 제한된 글은 주인만 지울 수 있다(제한된 줄을 내 방명록에서 치울 수 있게, 쓴 사람이 지우면 403). 신고는 더 하지 않는다.
  - 알림은 화면 안에서 하고 웹 푸시(아래 「웹 푸시」)로도 보낸다. 새 글 = 내 방명록의 지우지 않은 글 중 `users.guestbook_seen_at` 이후(NULL이면 전부 새 글, `users.created_at`은 Clock 값이 아니라 쓰지 않는다). 상단 바 프로필 사진의 빨간 점과 프로필 메뉴 「새 방명록 N」(`/me#guestbook`), 마이페이지·내 프로필의 NEW(처음 받은 `seenAt` 기준)로 보여주고, 0쪽이 보이면 화면에 보인 가장 최근 글의 시각으로 `seen`을 보낸다. 글 제한 경고 = 내가 쓴 글 중 `guestbook_warnings_seen_at` 이후에 제한된 글이고, `GuestbookWarningDialog`(AppLayout)가 첫 로드·경로가 바뀔 때만 가장 최근 경고를 보여주고 닫으면 `ack`한다.
  - 두 시각 컬럼은 `photo_key`처럼 엔티티에서 `insertable/updatable=false`이고 `UserRepository.markGuestbookSeen`·`ackGuestbookWarnings`(뒤로 가지 않고 지금을 넘지 않는 update)로만 바꾼다.
  - 쓰기·제한은 `GuestbookEntryCreatedEvent`·`GuestbookEntryRestrictedEvent`(ID만, 본문 없음)를 트랜잭션 안에서 발행한다. 웹 푸시(`PushNotifier`)가 커밋 뒤에 받는다.
  - 회원이 지워질 때: 주인이 강제 탈퇴하면 `owner_id`만 NULL(글·신고가 남아 관리자가 계속 처리한다), 쓴 사람이 강제 탈퇴하면 `author_id`만 NULL(「탈퇴한 사용자」). 강제 탈퇴는 회원을 지우기 전에 그 사람 글의 처리 전 신고를 「글 제한」으로 처리하고 글을 제한한다(`GuestbookService.restrictOpenReportsAgainst`, 경고 이벤트는 내지 않는다).
  - 화면: 멤버 프로필 모달의 [프로필 정보 | 방명록 N] 탭(지금 이 조직 멤버일 때만, 나면 입력창 없음)과 마이페이지 하단 「방명록」 섹션(주인이 모아 보는 곳, 입력창 없음). 다른 사람은 조직 화면에서 사람을 누르면 열리는 프로필 모달에서 쓴다.
- 웹 푸시(`push` 패키지, V20 `push_devices`, 이슈 #102, Notion 「20. 방명록 기능 추가」 2차): 새 방명록(→ 주인), 글 제한 경고(→ 쓴 사람), 새 쪽지(→ 받은 사람)를 FCM 웹 푸시로도 알린다. 화면 안 배지는 그대로 둔다(푸시를 못 받는 사람도 알 수 있게).
  - 기기는 FID(Firebase 설치 ID)다. `PUT /api/push/devices/{fid}`(200 `{created}`, FID 형식이 틀리면 400, 푸시가 꺼져 있으면 409, 세션 행이 없으면 401), `DELETE …`(204, 내 것만)로 등록·해제한다.
  - 기기는 로그인 세션(`spring_session.primary_id`)에 FK CASCADE로 묶여 로그아웃·30일 만료·강제 탈퇴 때 함께 지워진다(`SecurityConfig`는 그대로). 등록은 세션 id로 찾는 한 문장 upsert이고(같은 브라우저에서 다른 계정이 켜면 그 사람에게 옮겨 간다) 한 사람당 최근 10대까지 둔다. 보낼 때는 세션의 지금 로그인 주인(`principal_name` = google sub)이 기기 주인과 같은 기기만 쓰고, 다른 기기 행은 지운다(`PushDeviceRepository.deleteTakenOver`). 열린 세션에서 다른 사람이 로그인하면 primary_id가 그대로라서 그대로 두면 앞사람의 쪽지 알림이 뒷사람 브라우저로 간다.
  - 발송: 커밋 뒤 리스너(`PushNotifier`, AFTER_COMMIT) → 내부 `PushDispatcher`(2스레드, 큐 500, 넘치면 버린다. `@EnableAsync`·Executor 빈을 만들지 않는다) → 커밋된 데이터를 다시 읽어 문구를 정하고 트랜잭션 밖에서 `FirebasePushSender`(firebase-admin, `addAllFids`, Web Push TTL 1일·Urgency high·Topic = tag)로 보낸다. `UNREGISTERED`·`SENDER_ID_MISMATCH`로 답한 기기는 지우고(보내는 사이에 다시 등록한 기기는 남긴다), 그 밖의 실패는 오류 코드 이름만 경고 로그로 남긴다. 보내기는 10초에서 끊는다(SDK의 503 재시도는 공개 API로 줄일 수 없어 `FirebasePushSender`에 적어 두었다). 디스패처는 웹 서버가 멈춘 뒤·DB가 닫히기 전에 멈춘다(`SmartLifecycle`). 예외는 쪽지·방명록 API로 올라오지 않는다.
  - 데이터는 `{kind, title, url, tag}`만 보내고(data-only) 문구에는 이름만 넣는다(본문 없음): 「OO님이 방명록을 남겼어요」, 「남긴 방명록 글이 관리자에 의해 제한됐어요」, 「OO님이 쪽지를 보냈어요」·「OO님이 답장을 보냈어요」, 익명이면 「익명 쪽지가 왔어요」(`PushMessages`, 이름은 20자에서 자른다). 쪽지 문구는 받은 쪽지함과 같은 쿼리(`LetterRepository.findReceivedRow`)로 정해 익명·차단 규칙을 그대로 따르고, 차단으로 버린 쪽지는 `LetterDeliveredEvent`를 내지 않는다. 로그에는 kind와 개수만 남긴다(익명 쪽지의 받는 사람이 드러나지 않게).
  - 화면: 마이페이지 「알림」 카드(`components/NotificationSettings.tsx`, 03-N6·D03-N6)의 「이 기기에서 알림 받기」 스위치로 사람이 직접 켠다. 설정은 사람별 localStorage이고, `/api/config`에 push가 없으면 카드를 숨긴다. `enable`의 첫 문장은 `Notification.requestPermission()`이다(그 앞에 await가 있으면 iOS가 사용자 동작으로 인정하지 않는다). 켜 둔 사람은 앱을 열 때마다 조용히 다시 등록한다(`hooks/usePushSync.ts`, AppLayout). 로그아웃 전에 `releaseOnLogout()`(1.5초, 실패해도 로그아웃한다)을 부른다.
  - Firebase SDK(`firebase/app`·`firebase/messaging`)는 dynamic import이고 FID 방식(`register`·`onRegistered`·`onUnregistered`·`unregister`)을 쓴다. `getToken`은 deprecated라 쓰지 않는다(`lib/pushClient.ts`). 콜백은 `register`보다 먼저 걸고, 서버 PUT·DELETE는 부른 순서대로 보낸다. 서비스 워커를 새로 깔았거나 구독이 없거나 구독의 VAPID 키가 지금과 다르면 구독을 새로 만든다.
  - 서비스 워커 `public/firebase-messaging-sw.js`는 SDK 없이 쓴 순수 JS다(`tsconfig.sw.json`으로 검사, fetch 핸들러 없음). 크롬(Chromium)에서 오점왓 창이 포커스돼 있으면 알림 대신 창에 `ohjumwhat:push`만 보내 쿼리를 새로 고치고, 그 밖(Safari·Firefox 포함)에는 항상 알림을 띄운다(iOS는 알림 없는 푸시가 쌓이면 구독을 끊는다). 같은 tag는 하나로 묶되 다시 울린다(renotify). 누르면 같은 출처 경로만 연다(열린 창은 `ohjumwhat:navigate`, 없으면 새 창).
  - 아이폰·아이패드는 iOS 16.4 이상에서 홈 화면에 추가한 오점왓으로만 받는다(웹 앱 매니페스트 `public/manifest.webmanifest`, 아이콘 `public/icons/`). 홈 화면 앱은 Safari와 로그인이 따로다. Safari 탭·인앱 브라우저에서는 카드가 안내만 한다(`lib/push.ts pushSupport`).
- 이용 제한(제재)(`sanction` 패키지, V21 `user_sanctions`, 이슈 #114, Notion 「28. 사용자 제재 수단 추가」): 관리자가 한 번에 거는 조치 묶음이다. **제한**(`Restriction`: `SUSPEND` 활동 정지·`POLL` 투표 제한·`CHAT` 채팅 금지·`LETTER` 쪽지 금지·`GUESTBOOK` 방명록 쓰기 금지·`PROFILE` 프로필 수정 잠금) + **프로필 초기화**(`ProfileReset`: 별명·사진·소개·상세 프로필) + **사유**(`SanctionReason` 분류 + 관리자 설명 200자, 둘 다 본인에게 보인다). 제한도 초기화도 없으면 경고다.
  - 막는 범위: POLL은 참여를 뺀 투표 기능(투표 만들기·수정·지금 마감·삭제, 메뉴 추가·식당, 메뉴 댓글 쓰기·고치기, 정기 투표), CHAT은 보내기·고치기, LETTER는 보내기·답장, GUESTBOOK은 쓰기, PROFILE은 별명·사진·소개·상세 프로필, SUSPEND는 그 전부 + 조직 만들기·초대 참여·조직 이름·위치. **투표 참여(고르기·패스·취소)는 어떤 제재로도 막지 않는다.** 지우기·읽음·차단·신고·조직 탈퇴·알림 기기·로그아웃도 그대로다. 정기 투표가 여는 투표도 그대로 열린다.
  - 기간은 1·3·7·30일 또는 해제할 때까지(`ends_at` NULL). 마감처럼 배치 없이 요청 시각으로 판정하고, 관리자는 먼저 해제한다(`lifted_at`, 초기화는 되돌리지 않고 본인에게 알리지 않는다). 활동 정지는 혼자 저장한다(CHECK). 행은 회원과 함께 CASCADE로 지워진다.
  - 지금 비어 있는 프로필 항목의 초기화는 하지 않고 기록에서도 뺀다(본인 안내에 하지 않은 초기화가 보이지 않게, 화면도 빈 항목은 못 고른다). 관리자는 제재할 수 없지만, 제재 중인 회원을 나중에 `ADMIN_EMAILS`로 관리자로 지정하면 남은 제재는 그대로라 「제재」 탭에서 해제한다(관리자 API는 막히지 않는다). 확인과 쓰기는 한 트랜잭션이 아니라, 확인을 통과한 직후 걸린 제재는 그 요청에는 적용되지 않는다.
  - **유효 제한 규칙은 서버 `SanctionGuard`와 화면 `lib/sanctions.ts restrictionOf`가 같아서 함께 고친다**: 기능 X가 막힘 = 활성 제재(제한 있음·해제 안 됨·`ends_at > 지금`) 중 X 또는 SUSPEND가 든 것이 있음, 끝나는 시각 = 그중 가장 늦은 것(하나라도 무기한이면 무기한), SUSPEND가 있으면 활동 정지 문구.
  - 서버 차단은 컨트롤러의 `@Restricted(X)`·`@Unrestricted` + `SanctionInterceptor`(`/api/**`, admin 제외)다. 막히면 423 `{"message": "관리자가 채팅을 제한했어요(10월 14일 오후 6:00까지)."}`(`ApiException.locked`). 컨트롤러 앞에서 막아 `@Valid`·멤버 확인·속도 제한·외부 호출(카카오·naver.me)·사진 처리보다 먼저다. **새 쓰기 API를 만들면 둘 중 하나를 꼭 단다**(`SanctionCoverageTest`가 GET이 아닌 `/api/**`를 모두 확인하고, 막는 API 표와 개수도 맞춘다).
  - API: 본인 `GET /api/me`의 `sanctions`(활성 제재), `GET /api/sanctions/alerts`(본 적 없는 안내, 오래된 순 20개), `POST /api/sanctions/seen {ids}`(행마다 `seen_at`, 내 것만). 관리자 `POST /api/admin/users/{id}/sanctions {restrictions, resets, days, reason, note}`(회원 잠금 → 404 → 나·관리자 409 → 값 400, 초기화는 `UserRepository` update 쿼리 + 사진 파일은 커밋 뒤 삭제), `GET …/users/{id}/sanctions`, `POST /api/admin/sanctions/{id}/lift`(진행 중이 아니면 409), `GET /api/admin/sanctions?status=active|all`(상태 ACTIVE·EXPIRED·LIFTED·WARNING·RESET_ONLY는 서버가 계산). 개요 `Stats.restrictedUserCount`, 회원 목록·상세 `UserRow.restricted`.
  - 알림: 커밋 뒤 웹 푸시(`PushKind.SANCTION`, 제한 → 「관리자가 이용을 제한했어요」, 초기화만 → 「…프로필을 초기화했어요」, 경고 → 「관리자의 경고가 도착했어요」, 내용 없음). 다음 접속 때 안내 창(`SanctionNoticeDialog`, 오래된 것부터 하나씩 1 / N, 끝까지 넘기면 seen), 마이페이지 「이용 제한」 카드(`RestrictionCard`), 막힌 자리의 `RestrictionNotice`(`hooks/useRestriction.ts`, 끝나는 시각이 지나면 스스로 풀린다). 화면에서 막는 것은 안내용이고, 423을 받으면 `main.tsx`가 내 정보와 안내를 다시 받는다.
  - 한 번에 하나만 뜨는 안내 창은 `AppNoticeDialogs`(AppLayout)가 고른다: 제재 안내 → 방명록 글 제한 경고(`GuestbookWarningDialog`는 표시만 한다) → 신고 처리 결과. 첫 로드·경로가 바뀔 때 고르고, 「확인」으로 닫으면 같은 화면에서 바로 다음 것을, Esc·바깥으로 닫으면 다음 화면에서 고른다(Esc로 `<dialog>`가 먼저 닫힌 뒤 같은 화면에서 다시 고르면 열린 상태가 그대로라 창이 안 보인다). 닫기 핸들러는 지금 띄운 종류가 아니면 무시한다(「확인」 뒤 `<dialog>`의 close 이벤트가 한 번 더 온다).
- 사람 신고(`report` 패키지, V22 `profile_reports`, 이슈 #115, Notion 「28. 사용자 제재 수단 추가」): 같은 조직 멤버의 프로필 모달 「이 사람 신고하기」로 **사람**(프로필 내용과 행동)을 신고한다. 쪽지·방명록 신고와 따로다.
  - `POST /api/users/{userId}/report {reason, detail}`(204, `@Unrestricted`): 나 자신 400 → 대상이 없거나 조직을 같이 쓰지 않으면 404(`MembershipRepository.sharesOrganization`) → 사유(`SanctionReason` 분류) 400 → 설명은 쪽지 신고 사유와 같은 정리(한 줄, 100자). 같은 (신고자, 대상)에 처리 전 신고는 하나(부분 UNIQUE)라 다시 보내도 그대로 204다. 관리자를 신고해도 받는다(관리자 여부를 드러내지 않게, 「문제 없음」으로만 처리된다).
  - 신고할 때의 이름(별명 → 구글 이름)·한줄 소개·좋아하는 음식·취미·직급을 신고 행에 저장해(`target_*`) 관리자가 지금 값과 나란히 본다. 사진은 저장하지 않는다(바꾸면 옛 파일이 지워진다).
  - 처리: 관리자 「문제 없음」 → `DISMISSED`(`POST /api/admin/profile-reports/{id}/dismiss`, 다른 결과로 처리됐으면 409), **어떤 제재든(경고 포함) 그 사람에게 걸면** 처리 전 신고가 모두 `ACTIONED`(`SanctionService.apply`가 부른다, `sanction_id` + 실제로 저장된 제한·초기화·끝나는 시각의 사본 `result_*`), 강제 탈퇴는 회원을 지우기 전에 `WITHDRAWN`. 사람이 지워지면 `reporter_id`·`target_id`는 NULL이 되고 신고는 남는다.
  - **신고자에게 결과를 알린다**(사용자 결정): 커밋 뒤 웹 푸시 「신고 처리 결과가 도착했어요」(`PushKind.REPORT_RESULT`, 내용 없음)와 다음 접속 때 결과 창(`ReportResultDialog`, `GET /api/sanctions/alerts`의 `reportResults`, `POST /api/profile-reports/results/seen {ids}`). 조치 내용(제한·기간·초기화 / 경고 / 문제 없음 / 탈퇴 처리)은 보여주고 **관리자 설명은 보여주지 않는다**. 결과는 처리 순간의 사본이라 해제·탈퇴돼도 바뀌지 않는다.
  - 관리자: 「신고」 탭의 [쪽지 | 방명록 | 프로필](`?type=profile`, `GET /api/admin/profile-reports?status=open|all`)에서 신고할 때·지금 프로필을 비교하고 [문제 없음]·[제재하기](사유가 채워진 제재 창, 대상이 탈퇴했거나 관리자면 숨김). 제재 창은 처리 전 사람 신고 수(`UserDetail.openProfileReportCount`)를 안내한다. 개요 「처리할 신고」(`Stats.openReportCount`)는 쪽지·방명록·프로필의 합이고 `openProfileReportCount`도 준다.
- 메뉴 자동완성은 별도 테이블 없이 같은 조직 과거 투표의 `menu_options.name`을 중복 없이 조회해서 만든다. 항목마다 마지막으로 먹은 날을 붙이고, 최근 7일 안에 먹은 메뉴는 뒤로 보낸다.
- 메뉴 통계·추천(`menu/MenuStatsService`, 네이티브 SQL)도 별도 테이블 없이 계산한다. "먹은 메뉴"는 **마감된 투표에서 참여자가 한 명 이상인 메뉴**이고(조직 기준), 이름은 소문자·띄어쓰기 제거로 묶는다("김치찌개" = "김치 찌개", 표시는 가장 최근 이름). 추천은 먹은 적이 있지만 최근 7일(오늘 포함) 안에는 먹지 않은 메뉴를 많이 먹은 순으로 준다. 조직 「통계」 탭(`/orgs/:orgId/stats`)과 메뉴 입력창(비운 채 누르면 추천)에서 쓴다.
- 메뉴 채택 랭킹(메뉴 메이커, `ranking` 패키지, 조직 「랭킹」 탭 `/orgs/:orgId/ranking`, Notion 「15. 메뉴 채택 랭킹 기능 추가」)도 별도 테이블 없이 볼 때마다 계산한다(스키마 변경 없음).
  - **채택 규칙은 `ranking/MenuAdoption` 하나가 원본이다**(랭킹과 투표 상세 응답의 `adoption`이 함께 쓴다). 마감된 투표에서 메뉴를 고른 사람(패스 제외)이 가장 많은 메뉴 하나, 같으면 먼저 추가한 메뉴(id가 작은 쪽, 결과 화면의 확정 팀 순서와 같다)를 채택하고, 그 메뉴를 추가한 사람(`created_by`)에게 1회를 준다(그 메뉴를 고르지 않았어도). 강제 탈퇴로 `created_by`가 NULL이면 아무도 받지 않는다(2위에게 넘기지 않는다).
  - 최소 인원: `메뉴를 고른 사람 × 10 ≥ 마감 당시 인원 × 3`(30% 올림). 마감 당시 인원은 「`joined_at ≤ closes_at`인 지금 멤버」와 「그 투표에 응답한 사람」의 합집합이다(조직을 떠나면 멤버 행이 지워져 과거 인원을 그대로 알 수 없어 이렇게 추정한다. 마감 뒤에 들어온 사람은 빠져 지난 기록이 바뀌지 않는다). 못 미치면 채택이 없고 결과 화면이 안내한다(Figma 05b-W2).
  - `GET /api/orgs/{id}/ranking?period=week|month&date=`: 기간은 `poll_date`(한국) 기준 주(월~일)·달(1일~말일)이고, 진행 중인 투표는 마감된 뒤에 들어간다. 이번 달 1일의 12개월 전이 들어 있는 기간까지 보고, 그보다 앞이나 앞으로의 날짜는 400이다(기록은 지우지 않는다). 기간 규칙은 `RankingPeriod`와 화면 `lib/ranking.ts`가 같아서 함께 고친다.
  - 지금 멤버만 순위에 넣는다(조직을 떠나면 바로 빠지고, 다시 들어오면 돌아온다). 같은 횟수는 같은 순위(1, 1, 3)이고, 보여주는 순서는 그 횟수에 먼저 도달한 사람(마지막 채택 투표의 마감이 이른 사람) → 이름이다. 대표 메뉴는 통계와 같은 이름 키(`MenuStatsService.key`)로 묶어 가장 많이(같으면 최근에) 채택된 메뉴이고, 표시는 가장 최근 이름이다. 사람별 채택 메뉴 목록이 응답에 들어 있어 채택 기록 모달은 따로 부르지 않는다.
  - 칭호: TOP3는 🥇 명예 셰프 · 🥈 기획왕 · 🥉 미식가(같은 순위면 같은 칭호). 「이달의 메뉴 메이커」(🏅)는 지난달 월간 1위(공동이면 모두)에게 이번 달 동안 멤버 목록·멤버 프로필에만 붙는다(`queries/ranking.ts useMonthlyMakers`).
  - 강제 탈퇴로 그 사람의 응답이 CASCADE로 지워지면 지난 투표의 채택이 바뀔 수 있다(드물어서 받아들였다). 「메뉴 연주자」(연속 채택) 칭호는 '연속'의 정의를 PM과 정한 뒤 2차로 한다.

**관리자 콘솔**(`admin` 패키지, 프론트 `/admin`, 요구사항: Notion 「superadmin 정의」)
- 관리자는 `users.role = ADMIN`이고, 서버 설정 `ohjumwhat.admin.emails`(환경변수 `ADMIN_EMAILS`, 쉼표로 여러 개)로만 지정한다. 저장소가 공개라 이메일을 코드·마이그레이션에 넣지 않는다.
  - 서버가 뜰 때 `AdminBootstrap` → `UserService.syncAdmins()`가 목록과 맞춘다(목록에 없는 관리자는 해제, 설정이 비어 있으면 아무것도 바꾸지 않음).
  - 구글 로그인 때도 이메일이 인증된 계정이면 관리자로 지정한다(`UserService.login`).
- 권한은 `/api/admin/**`에서 `auth/AdminAuthorizationManager`가 **요청마다 DB의 role로** 확인한다(세션에 권한을 넣지 않아 지정·해제가 바로 반영된다). 아니면 403 `{"message"}`. 프론트 `AdminLayout`은 `me.admin`으로 화면만 가린다(관리자가 아니면 `NotFoundPage`).
- 강제 탈퇴(`AdminService.withdraw`, 한 트랜잭션): 회원 행 잠금 → 소속 조직마다 `OrganizationService.removeMember`(탈퇴와 같은 규칙, 마지막 멤버면 조직 삭제) → 쪽지·방명록의 처리 전 신고 처리(방명록은 글 제한), 그 사람에 대한 처리 전 사람 신고는 「탈퇴 처리」 → `blocked_accounts`에 구글 계정 기록 → 회원 삭제 → `spring_session`에서 `principal_name = google sub`인 세션 삭제(`JdbcTemplate`, 세션 저장소 API는 별도 트랜잭션이라 쓰지 않는다).
  - 회원을 지우면 응답(votes)은 CASCADE로 지워지고, 올린 메뉴는 `menu_options.created_by`만 NULL이 된다. 응답·화면에서는 `createdBy: null` → "탈퇴한 사용자"로 보여준다.
  - 차단된 구글 계정의 로그인은 `GoogleOidcUserService`가 `OAuth2AuthenticationException("account_blocked")`로 거절하고, 실패 핸들러가 `/login?error=blocked`로 보낸다. 차단은 콘솔 「차단」 탭에서 풀 수 있다.
  - 자기 자신과 다른 관리자는 강제 탈퇴할 수 없다(409). 회원 행이 없어진 세션의 `/api/me`는 401이고 세션을 끝낸다.
- 강제 탈퇴보다 가벼운 조치는 이용 제한(위 「이용 제한(제재)」)이다. 회원 상세 「이용 제한」 카드(제재하기·해제·이력, 관리자·나 자신은 숨김)와 「제재」 탭(`/admin/sanctions`, 진행 중·전체)에서 다루고, 쪽지·방명록 신고 행의 「제재하기」로도 연다(쪽지 신고는 제재하면 처리 완료). 제재 로직은 `AdminService`가 아니라 `sanction/SanctionService`·`SanctionAdminController`에 둔다.
  - 강제 탈퇴하면 올린 프로필 사진 파일도 커밋한 뒤에 지운다.
- 회원 상세의 「올린 사진 지우기」(`DELETE /api/admin/users/{id}/photo`, 204)는 부적절한 사진 대응용이다. 구글 사진으로 돌아가고, 올린 사진이 없으면 아무것도 하지 않는다.
- 회원 상세의 「프로필 수정」(이슈 #93, Figma `A03-M3`)은 부적절한 프로필 대응용이다. 별명(비우면 구글 이름, 구글 이름은 로그인 때마다 바뀌어 고칠 수 없다)·한줄 소개·좋아하는 음식·상세 프로필을 고치고 지운다. 사진 지우기처럼 본인에게 알리지 않고, 다른 관리자와 자기 자신도 고칠 수 있다.
  - API: `PUT /api/admin/users/{id}/nickname {nickname}`, `PUT …/profile {bio, foodTags}`, `PUT …/profile/details {mbti, personalColor, hobbies, age, jobTitle}`, `DELETE …/profile/details`. 모두 회원 상세(`GET /api/admin/users/{id}`와 같은 응답, 비교용 `nickname` 포함)를 돌려주고, 없는 회원은 404다(요청 형식·크기 검사(`@Valid`) 다음이라 형식이 틀리면 400이 먼저다).
  - 규칙·오류 문구는 마이페이지와 같다: `UserService`의 `saveNickname`·`saveIntro`·`saveDetails`를 마이페이지와 함께 쓴다(모두 update 쿼리로 저장한다). `AdminService`가 사진 지우기처럼 회원 행을 잠그고 부르고, 로그에는 값 없이 `adminId`·`userId`와 바뀐 부분만 남긴다.
  - 상세 프로필은 「모두 비었거나 모두 채워졌거나」(DB CHECK)라 고치려면 다섯 항목이 모두 있어야 하고, 「상세 프로필 지우기」(`UserRepository.clearDetails`, 취미는 NOT NULL이라 빈 배열)는 다섯 항목을 한꺼번에 비운다(확인 창 `A03-M4`, 「저장」을 누르지 않아도 바로 지워진다). 본인이 다음에 들어오면 다시 채우기 안내(`03-N4`)를 본다.
  - 화면(`AdminProfileModal`)은 바뀐 것만 이름 → 소개 → 상세 프로필 순서로 보낸다. 상세 프로필이 비어 있던 회원은 비워 둔 채 저장할 수 있고(채우기 시작했으면 다섯 항목 모두, 「입력 비우기」로 되돌린다), 채운 회원은 고치거나 지운다.
- `OrganizationService.leave`(본인 탈퇴, 없으면 404)와 `removeMember`(관리자용, 없으면 아무것도 안 함)는 같은 내부 로직을 쓴다. 같은 트랜잭션 안에서 예외를 내면 트랜잭션 전체가 롤백되므로 관리자 작업은 `removeMember`를 쓴다.
- 진행 중인 정기 투표를 지우면 스케줄러가 1분 안에 다시 열기 때문에, 투표 삭제는 `withSchedule`로 규칙도 함께 지울 수 있다.
- 「신고」 탭(`/admin/reports`)은 [쪽지 | 방명록 | 프로필] 전환(`?type=guestbook`·`?type=profile`, 프로필은 위 「사람 신고」)과 [처리 전 | 전체]로 나눈다. 쪽지는 받은 사람이 신고한 쪽지만 보여준다(위 「쪽지」, 신고되지 않은 쪽지는 관리자도 볼 수 없다). 방명록은 주인이 신고한 글을 원문(제한·삭제돼도)과 함께 보여주고 [문제 없음]·[글 제한](확인 창)으로 처리한다(`GET /api/admin/guestbook-reports?status=`, `POST …/{id}/restrict`·`dismiss`). 개요의 「처리할 신고」(`Stats.openReportCount`)는 쪽지·방명록·프로필 셋의 합이고 `openLetterReportCount`·`openGuestbookReportCount`·`openProfileReportCount`도 준다(카드는 처리 전 신고가 있는 첫 종류의 탭을 연다).
- 조회 쿼리는 `admin/AdminRepository`(JPQL `select new AdminResponses$...`)에 모아 둔다. 목록은 검색어 `q`, 최대 100건이다.

**새 소식**(`notice` 패키지, 프론트 `/notices`, Notion 「14. 공지사항 기능」)
- 업데이트(`RELEASE`, 릴리스 노트)와 개발자 노트(`NOTE`)를 `notices` 테이블(V6) 하나에 둔다. 글을 고쳐도 `published_at`은 그대로라 다시 알리지 않는다.
- 업데이트 글의 원본은 저장소 파일 `ohjumwhat-backend/src/main/resources/release-notes/{버전}.md`(첫 줄 `# 제목`, 나머지 본문)다.
  - 서버가 뜰 때 `ReleaseNoteSync`(ApplicationRunner)가 DB와 맞춘다. 새 버전은 지금 게시하고(게시 시각 = 배포 시각), 내용이 바뀐 글은 게시 시각을 두고 고친다. 파일이 없어져도 지우지 않는다.
  - 형식이 잘못된 파일은 건너뛰고, 어떤 오류도 서버 시작을 막지 않는다(시작 실패 = 헬스 체크 실패 = 서비스 중단). 실제 파일 형식은 `ReleaseNoteTest`가 CI에서 확인한다.
  - 테스트에서는 `ohjumwhat.release-notes.enabled=false`로 끄고 `NoticeService.syncReleaseNotes`를 직접 부른다(시작 때 넣은 행이 첫 테스트까지 남기 때문).
- 개발자 노트는 관리자 콘솔 「공지」(`/api/admin/notices`)에서 쓰고 고치고 지운다. 업데이트 글은 콘솔에서 고칠 수 없다(409).
- 안 읽은 공지 = `published_at > coalesce(users.notices_seen_at, users.created_at)`. 새로 가입한 사람에게 지난 공지는 안 읽음이 아니다. 공지별 읽음은 없다.
  - `/notices`를 열거나 배너를 닫으면 `POST /api/notices/seen`이 `notices_seen_at`만 update한다(엔티티를 저장하면 같은 순간의 로그인을 덮어쓸 수 있다). 거꾸로 로그인이 옛 시각을 되써서 읽음이 풀리지 않도록, 이 컬럼도 `photo_key`처럼 엔티티에서 `insertable/updatable=false`이고 `UserRepository.markNoticesSeen`으로만 바꾼다.
  - `users.created_at`은 `Clock`이 아니라 실제 시각이다. 안 읽음 테스트의 게시 시각은 회원의 실제 가입 시각을 기준으로 정한다.
- 화면: 상단 바 종 아이콘(`NoticeBell`, 안 읽으면 점), 조직 홈 맨 위 배너(`NoticeBanner`, 속한 조직이 없으면 마이페이지, 투표 상세에는 없음), `/notices`(`NoticesPage`, 연 시점 기준 NEW).
  - 본문은 일반 텍스트다(`lib/noticeBody.ts`): 빈 줄 = 문단, "- " = 목록, http(s) 주소 = 새 탭 링크. HTML·마크다운은 해석하지 않는다.
- 새 버전 안내: Vite가 빌드 ID와 버전을 코드(`__BUILD_ID__`, `__APP_VERSION__` = `package.json` 버전)와 `dist/version.json`에 넣는다. 운영 빌드에서 `/version.json`을 읽어(`queries/build.ts`, 창이 다시 보일 때와 5분마다) 빌드 ID가 다르면 `UpdateToast`를 띄운다.
  - 「새 소식」의 현재 버전도 화면 코드의 값이 아니라 `/version.json`의 버전이다(`useDeployedVersion`). 배포 전부터 열려 있던 탭도 배포된 버전을 보여준다. 못 읽으면(개발 서버) `__APP_VERSION__`을 쓴다.

**투표 화면.**
- 투표 상세(`PollDetailPage`)는 진행 중일 때 3초마다 폴링하고, 마감 응답을 받으면 결과 모드로 바뀌며 폴링을 멈춘다. 채팅만 WebSocket으로 받고(위 「투표 채팅」), 받기 연결은 결과 모드로 바뀌어도 끊기지 않게 이 화면이 든다.
- 참여·패스·취소는 `lib/pollDetail.ts`의 `applyVote`(`VoteChoice` = 메뉴 ID·`'PASS'`·`'NONE'`)로 먼저 화면에 반영(낙관적 업데이트)하고, 실패하면 되돌린다. 이 함수는 서버 `PollService.detail`과 같은 규칙으로 다시 계산하므로, 규칙을 바꿀 때는 둘을 함께 고친다.
  - `useVote`는 같은 투표의 요청을 mutation `scope`로 묶어 보낸 순서대로 처리한다(빠르게 두 번 누르면 참여 → 취소 순서가 지켜진다). 뒤에 기다리는 요청이 있으면 앞 요청의 응답을 캐시에 넣지 않는다.
- 화면마다 `useDocumentTitle(...)`로 탭 제목을 붙인다(예: "점심 · 개발팀 · 오점왓"). 없는 경로는 `NotFoundPage`가, 예상하지 못한 렌더링 오류는 `RouteErrorPage`(라우터 errorElement)가 처리한다.
- 마감 결과의 「결과 복사」는 `lib/share.ts`의 `resultText`(메신저에 붙일 글)와 `copyText`(클립보드, 안 되면 숨긴 입력창)로 한다.
- 시간 표시는 `lib/time.ts`(한국 시간 기준)를 쓴다. 요일 비트마스크(월=1 … 일=64, 평일=31)는 `lib/daysOfWeek.ts`로 변환한다.

**프론트엔드.** 라우트는 `src/router.tsx` 한 곳에 모여 있다(기획서의 화면 7개 + `/` 진입 분기 + 새 소식·관리자 콘솔). `/login`을 뺀 모든 화면은 `RequireAuth`(내 정보 조회가 401이면 경로를 기억하고 로그인 화면으로 보냄) 아래에 있다. API 호출은 `lib/api.ts`의 `api()`로만 하고, 서버 상태 훅과 query key는 `src/queries/`에 둔다. `/orgs/:orgId` 아래 화면은 `OrgLayout`이 조직 조회(방문 기록 갱신), 404 처리, 탭을 맡고, 하위 화면은 `useOrganization(orgId)` 캐시를 그대로 쓴다. 버튼·입력창 스타일은 `lib/ui.ts`(`buttonClass`, `inputClass`)에 있고, 모달은 네이티브 `<dialog>` 기반 `Modal`/`ConfirmDialog`를 쓴다. 이름처럼 사용자가 정한 말 뒤의 조사는 `lib/josa.ts`(`josa`, `withJosa`)로 붙인다. 끝 글자의 받침(숫자는 읽는 소리)으로 고르고, 한글·숫자로 끝나지 않으면 `이(가)`처럼 병기한다. 다른 쿼리나 뮤테이션이 401을 받으면 `main.tsx`의 캐시 핸들러가 내 정보를 다시 불러오고, 그 결과로 로그인 화면으로 이동한다. 서버 상태는 TanStack Query로 관리한다. 투표 상세 화면은 WebSocket을 쓰지 않고 진행 중일 때 몇 초 간격으로 폴링한다(10~20명 규모). 스타일은 Tailwind v4(`@import 'tailwindcss'`, `@tailwindcss/vite` 플러그인)다.

**반응형.** 1024px(`lg`) 미만은 모바일 레이아웃을 가운데 768px로 보여주고, `lg` 이상은 Figma 「와이어프레임 · 데스크톱」대로 콘텐츠 폭 1024px에 본문 + 오른쪽 사이드(320px) 2단이다(`AppLayout`, `lib/ui.ts`의 `columnsClass`).
- DOM 순서는 모바일 순서로 둔다. 사이드로 보낼 카드가 모바일 순서 중간에 있으면 grid 위치 클래스(`lg:col-start-2` 등)로 옮긴다(예: `MyPage`, `OrgSettingsPage`).
- 데스크톱에서만 보이는 요소는 `hidden lg:block`, 모바일에서만 보이는 요소는 `lg:hidden`으로 둔다(예: 조직 홈의 정기 투표 요약, 투표 상세의 응답 현황).
- 로그인 화면은 데스크톱에서 좌우로 나뉜다(왼쪽 소개·투표 미리보기, 오른쪽 로그인).

## 배포

- `main`에 push하면 `.github/workflows/deploy.yml`이 다음 순서로 실행된다.
  1. `ci.yml`(백엔드 테스트, 프론트 린트·테스트·빌드)
  2. 루트 `Dockerfile`로 이미지를 만들어 `ghcr.io/matuna01/ohjumwhat:latest`와 `:<sha>`로 올린다.
  3. SSH로 서버 `~/ohjumwhat`에 `deploy/` 파일을 복사하고, `docker compose pull && up -d`를 실행한다.
  4. 헬스 체크를 한다.
- `ci.yml`은 모든 PR에서도 돈다.
- 이미지를 만들 때 `application.example.yml`을 `application.yml`로 복사한다. 그래서 설정 키를 추가하면 예시 파일에도 반드시 넣어야 운영에 반영된다. 실제 값은 서버 `~/ohjumwhat/.env`(템플릿: `deploy/.env.example`)에 둔다.
- Caddy가 HTTPS를 맡고, `X-Forwarded-*` 헤더로 원래 주소를 넘긴다. 그래서 앱은 https 리디렉션 URI를 만들고, `Secure` 쿠키를 쓴다.
- 서버 설정, Secrets, 롤백, 백업·복원 방법은 `docs/DEPLOY.md`에 있다.
- 올린 프로필 사진은 Docker 볼륨 `photos`(앱 컨테이너 `/data/photos`, 소유자 `app`)에 있다. `deploy/backup.sh`가 매일 DB와 함께 tar.gz로 백업하고, 복원도 같은 시각의 DB와 사진을 함께 한다.
- **모니터링**(이슈 #107, Notion 「27. 서버 모니터링 구축」): 서버에 직접 설치한 Netdata(서버·컨테이너 지표) + 앱 지표 + 외부 업타임 체크. 설치·설정은 `docs/DEPLOY.md` 「모니터링」에 있다.
  - actuator는 관리 포트 8081에서만 연다(`health`, `prometheus`). Caddy는 앱 포트 8080만 프록시하므로 지표는 인터넷에 나가지 않고, 운영 compose는 8081을 서버의 127.0.0.1에만 연다. Netdata가 `/actuator/prometheus`(요청 수·응답 시간 `http_server_requests`, JVM 힙, DB 커넥션 풀 `hikaricp`, 채팅 연결 수 `ohjumwhat_chat_connections`(`chat/ChatMetrics`))를 읽고, 대시보드는 SSH 터널로만 본다.
  - 관리 포트는 인증 없이 열린다(`SecurityConfig`가 `/api/**`만 막고 관리 포트에도 같은 체인이 걸린다). 그래서 네트워크(127.0.0.1·Caddy가 8080만 프록시)로만 막는다.
  - 공개 헬스 체크는 앱 포트의 `/healthz`(health 그룹 `public`의 `additional-path`, 상태만)다. `db, diskSpace, readinessState`만 보고(시작·종료 중 503), 새 의존성이 저절로 들어오지 않게 하나씩 적는다. 배포 헬스 체크와 외부 업타임 체크(키워드 `"status":"UP"`)가 쓴다. 앱 포트의 `/actuator/**`는 없는 경로라 SPA가 index.html을 200으로 주므로, 업타임 체크는 상태 코드가 아니라 키워드로 본다.
  - 저장소 호출 지표(`spring.data.repository`)는 시계열만 늘려서 끈다(`management.metrics.enable`).
  - 테스트는 지표 내보내기가 꺼져 있어(Spring Boot 기본) `@AutoConfigureMetrics`로 켠다(`ManagementEndpointsTest`, 실제 포트, 별도 컨텍스트). 테스트 설정의 관리 포트는 0(빈 포트)이다.
  - 운영 이미지는 `application.example.yml`을 쓰고 테스트는 test `application.yml`만 읽으므로, `ManagementConfigTest`가 예시 파일의 관리 포트·공개 범위를 확인하고 두 파일의 `management.*`가 포트 말고 같은지 본다. `management` 설정을 바꾸면 두 파일을 함께 고친다.
  - 컨테이너 로그는 compose `x-logging`으로 서비스마다 10MB × 5개까지만 둔다(로그 설정이 바뀌면 `up -d`가 그 컨테이너를 다시 만든다).
- `dev` → `main` 승격 하나가 릴리스 하나다. 승격 전에 버전(`build.gradle.kts`, `package.json`)을 올리고 `CHANGELOG.md`를 적는다. 배포 뒤에는 `vX.Y.Z` 태그와 GitHub Release를 만든다(`docs/DEPLOY.md` 「릴리스와 버전」).
  - 사용자에게 보이는 변경이 있으면 업데이트 글 `ohjumwhat-backend/src/main/resources/release-notes/X.Y.Z.md`도 함께 적는다. 배포되면 새 소식에 자동으로 올라간다. CHANGELOG는 개발자용, 업데이트 글은 사용자용이다(사용자 말투 3~5줄, DB·마이그레이션 같은 개발 용어는 쓰지 않는다).
- 보안 헤더
  - HSTS·nosniff·X-Frame-Options는 Spring Security가 붙인다.
  - Referrer-Policy·Permissions-Policy·**CSP**는 `deploy/Caddyfile`이 붙인다. Caddyfile이 바뀌면 배포 스크립트가 검증 후 Caddy 컨테이너를 다시 만든다(단일 파일 마운트라 `up -d`만으로는 반영되지 않는다).
  - CSP가 허용하는 외부 출처는 Google Fonts, `*.googleusercontent.com`(프로필 사진), 네이버 지도, Firebase 푸시(`connect-src`의 `https://firebaseinstallations.googleapis.com`·`https://fcmregistrations.googleapis.com`, SDK는 번들에 들어 있다)뿐이다. 서비스 워커와 매니페스트는 `worker-src`·`manifest-src 'self'`다. 네이버 지도 스크립트(maps.js)는 **페이지 스킴에 따라 출처를 바꾼다**: https면 `*.pstatic.net`(타일 스타일 JSONP·타일·로고·커서), http면 `*.map.naver.net`·`static.naver.net`이고, 둘 다 `oapi.map.naver.com`(스크립트·인증)과 `kr-col-ext.nelo.navercorp.com`(오류 수집)을 쓴다. 네이버 출처는 스킴 없이 적어 운영은 https만 허용한다. 지도 스크립트가 style 속성을 직접 넣어서 `style-src-attr`만 `'unsafe-inline'`이다(`<style>` 태그·스크립트는 막는다). 새 외부 리소스(스크립트, 폰트, 이미지 CDN, 분석 도구)를 추가하면 CSP도 함께 고친다. 안 고치면 운영에서만 막힌다(개발 서버에는 CSP가 없다).
  - 투표 채팅 받기(WebSocket)는 같은 출처의 `wss://`다. `'self'`가 wss를 포함하지 않는 브라우저(구형 Safari)가 있어 `connect-src`에 `wss://{$APP_DOMAIN}`을 따로 적었다. 배포(앱 재시작) 때 채팅 연결이 끊기고 화면이 스스로 다시 연결한다.
  - **CSP는 https로 확인한다.** http로만 확인했다가 v1.7.0 운영에서 지도 타일이 막혔다. 자체 서명 인증서로 https 서버를 띄워 Caddyfile의 CSP를 그대로 붙이고, 헤드리스 Chrome(`--ignore-certificate-errors`, CDP)에서 `securitypolicyviolation` 이벤트가 0건인지 본다(앱 내 브라우저는 자체 서명 https를 열지 않는다).
- `Dockerfile`이나 `deploy/`를 바꾸면, 합치기 전에 로컬에서 `docker build`와 `deploy/docker-compose.yml`로 스택을 띄워 확인한다(도메인은 `localhost`).

## 디자인 시스템 (Figma) — 프론트엔드는 이것을 기준으로 개발한다

- Figma 파일: https://www.figma.com/design/w0OIV1khSVnxlf5KfRo6aP/OhJumWhat
  - 「디자인 시스템」 페이지
    - Foundations 프레임: 로고, 컨셉 컬러, 원색 팔레트, 의미 기반 토큰, 타이포그래피, 간격·둥글기·그림자
    - Components 프레임: Button, Badge, Avatar, OptionCard, Input(Error 포함), Logo, TopBar(「쪽지 배지」·「새 소식 점」 속성), UpdateToast, MapPin(지도 핀), ChatMessage·ChatPanel, Chat Unread(UnreadBadge, ChatPreview, NewMessagesPill, UnreadDivider, ChatButton), Profile Details(ChoicePill, MbtiPicker, Select, TagChip, SuggestChip, TagInput, ProfileDetailList), Letters(AnonymousAvatar, LetterItem, Checkbox, Textarea), Ranking(PeriodSwitcher, PodiumItem, RankRow, MyRankCard, AdoptedBadge, MakerBadge), Guestbook(GuestbookEntry, GuestbookInput, Pager, ProfileTabs, ProfileMenu), TopBar 「새 방명록 점」 속성, Push(Switch, PushSettingsCard: 꺼짐·켜짐·iOS 홈 화면 안내·차단됨·지원 안 함·오류), Sanctions(RestrictionNotice: 기간·해제할 때까지·활동 정지, RestrictionCard, SanctionNotice: 제한·활동 정지·경고·초기화·해제됨, ReportResult, SanctionRow)
    - PWA 아이콘 프레임: `PWA Icon 192`·`PWA Icon 512`·`PWA Icon Maskable 512`(안전 영역 80% 안)·`Notification Badge 96`(흰 실루엣, 배경 투명)
  - 「와이어프레임 · 모바일」 페이지: 모바일(390px) 화면. 소개 페이지(`00-W`, 로그인 전 `/`), 01 로그인부터 07 조직 설정까지와 `-M` 모달, 별명·프로필 사진·소개·상세 프로필(`03-N` 마이페이지, `03-N2` 소개 비었을 때, `03-N4` 상세 프로필 비었을 때, `03-M2` 프로필 수정(상세 프로필 섹션), `03-M2E` 상세 프로필 비었을 때·오류, `03-M3` 사진 맞추기), 멤버 프로필(`07-P`, 소개 없음·나·떠난 멤버 `07-P2`), 지난 투표(`04-H`), 새 소식 배너(`04-B`), 결과 복사(`05b-S`), 패스한 상태(`05-P`, 「✓ 오늘은 패스했어요 · 다시 누르면 취소」), 투표 관리(`05-A` ⋯ 메뉴, `05-M1` 수정, `05-M2` 지금 마감, `05-M3` 삭제), 식당 붙이기(`05-L`, `05-M4` 식당 모달), 식당 찾기 모달(`05-F`), 투표 지도(`05-G`, 크게 보기 `05-G2`), 메뉴 추천(`05-R`, 지난 식당·고른 식당 칩 `05-R2`), 메뉴 댓글(`05-K` 진행 중, `05b-K` 마감 결과 읽기 전용), 투표 채팅(`05-C` 하단 버튼·안 읽은 배지, `05-C2` 채팅 시트, `05-C3` 닫힌 채팅, `05-C4` 새 메시지 도착 미리보기, `05-C5` 「여기부터 새 메시지」, `05-C6` 위로 올려 읽는 중 「새 메시지 N ↓」), 조직 홈 안 읽은 채팅(`04-C`), 조직 위치(`07-L`, 조직 주소·반경·지도), `08 통계`, `09 새 소식`, 쪽지(`10` 받은 쪽지, `10b` 보낸 쪽지, `10c` 비었을 때, `10-M1` 쓰기·받는 사람 고정, `10-M1b` 받는 사람 고르기, `10-M1c` 답장·익명 유지, `10-M2` 받은 쪽지 보기, `10-M2b` 익명 쪽지, `10-M2c` 보낸 쪽지, `10-M3` 신고, `10-M4` 차단한 사람 관리, `10-M5` 차단 확인), 메뉴 채택 랭킹(`11` 주간, `11b` 월간·지난 기록, `11c` 비었을 때, `11-M` 채택 기록 모달), 조직 홈 랭킹 요약(`04-R`), 마감 결과 채택 표시(`05b-W`, 참여 30% 미달 `05b-W2`), 이달의 메뉴 메이커(`07-P3`), 방명록(`07-P`·`07-P2` 탭, `07-P4` 멤버 프로필 · 방명록, `07-P4b` 비었을 때, `07-P4c` 쓴 직후 5초 대기, `07-P4d` 제한된 글·탈퇴한 사용자, `07-P5` 내 프로필 · 방명록, `07-M2` 신고, `07-M3` 지우기 확인, `07-M4` 글 제한 경고, `03-N5` 마이페이지 · 방명록, `03-N5b` 비었을 때, `03-N5c` 상단 바 새 방명록 점·프로필 메뉴), 웹 푸시(`03-N6` 마이페이지 · 알림, `03-N6b` 아이폰 Safari 안내, `03-N6c` 푸시 알림 예시), 이용 제한(`00-S` 제재 안내 창, `03-N7` 마이페이지 · 이용 제한, `05-X` 채팅 금지, `05-X2` 투표 제한, `10-X` 쪽지 금지), 사람 신고(`07-P6` 「이 사람 신고하기」, `07-M5` 신고, `00-R` 신고 결과 창), 진행 중·지난 투표 구분(`04-S` 진행 중·오늘 마감 나누기·지난 투표 날짜와 「마감」, `05b-D` 지난 투표 날짜 줄)
  - 「와이어프레임 · 데스크톱」 페이지: 같은 화면의 데스크톱(1440px) 버전(`D00-W` 소개 페이지, `D01`~`D07-M`, `D03-N`, `D03-N4`, `D03-M2`·`D03-M2E` 프로필 수정, `D07-P` 멤버 프로필, `D04-H`, `D05-A`, `D05-G`, `D05-G2`, `D05-K`, `D05-C`, `D05-C2` 채팅 카드가 화면 밖일 때, `D07-L`, `D08`, `D09`, `D10`·`D10b`·`D10c` 쪽지함과 `D10-M1`~`D10-M5`, `D11` 랭킹, `D04-R` 조직 홈 사이드 랭킹 카드, `D05b-W` 채택 표시, `D07-P`(탭)·`D07-P4` 조직 홈 사이드 멤버 목록에서 연 방명록, `D03-N5` 마이페이지 · 방명록, `D03-N6` 마이페이지 · 알림, `D03-N7` 마이페이지 · 이용 제한, `D05-X` 투표 제한·채팅 금지). 모달은 모바일 `-M` 프레임과 같다(큰 지도 모달 `D05-G2`만 넓다).
  - 「관리자 콘솔」 페이지: 관리자 화면(모바일 `A01`~`A09`, 데스크톱 `DA01`~`DA09`, 쪽지 신고 `A09`·`DA09`, 방명록 신고 `A09b`·`DA09b`·`A09b-M` 글 제한 확인, 개요의 처리할 신고 카드(쪽지·방명록 합계), 강제 탈퇴 모달 `-M`, 올린 사진 지우기 `A03-M2`, 프로필 수정 `A03-M3`, 공지 글쓰기 모달 `A08-M`, 메뉴 댓글 지우기 `A06-K`, 채팅 지우기 `A06-C`, 차단된 로그인 `L01`, 이용 제한 `A03b`·`DA03b` 회원 상세·`A03-M5` 제재하기·`A03-M6` 해제 확인·`A10`·`DA10` 「제재」 탭, 프로필 신고 `A09c`·`DA09c`, 개요 「제한 중」 카드, 회원 목록 「제한 중」 배지)과 로컬 컴포넌트 StatCard·ListRow
    - 콘텐츠 폭 1024px 가운데 정렬. 1024px 이상(`lg`)에서 본문 + 오른쪽 사이드(320px) 2단, 그보다 좁으면 모바일 레이아웃을 쓴다.
    - 로그인은 좌우 분할(왼쪽 브랜드 소개·투표 미리보기, 오른쪽 로그인), 모달은 폭 448px이다.
- **새 화면이나 컴포넌트를 만들 때는 먼저 해당 Figma 프레임을 보고 그대로 구현한다.**
  - 디자인과 다르게 구현해야 하면 이유를 PR에 적는다.
  - Figma는 원격 Figma 커넥터의 `use_figma`로 그린다(이 계정에 파일 편집 권한이 있다). 부르기 전에 `figma:figma-use` 스킬을 불러온다.
- **색은 의미 기반 토큰 클래스만 쓴다.**
  - 입력 오류의 빨간 테두리는 `border-border-danger`(Figma `color/border/danger`)다. `inputClass`가 `aria-invalid`일 때 붙인다.
  - 예: `bg-bg-canvas`, `bg-bg-brand`, `text-text-secondary`, `border-border-default`
  - `stone-*`, `orange-*` 같은 원색 클래스는 화면 코드에서 쓰지 않는다.
  - 토큰은 `ohjumwhat-frontend/src/index.css`의 `@theme`에 있고, Figma `Color` 변수와 이름이 1:1이다(`color/bg/canvas` → `--color-bg-canvas` → `bg-bg-canvas`).
  - 토큰을 추가하거나 바꿀 때는 Figma 변수와 `@theme`를 함께 고친다.
- **글꼴은 Noto Sans KR**(`index.html`에서 Google Fonts로 불러옴)이다.
  - 굵기는 400(본문) / 500(버튼·라벨·배지) / 700(제목·강조) / 900(로고)만 쓴다. `font-semibold`는 쓰지 않는다.
  - 텍스트 스타일 대응: H1=`text-2xl font-bold`, H2=`text-lg font-bold`, H3=`font-bold`, Small=`text-sm`, Caption=`text-xs`.
- 링크 미리보기 이미지(`public/og-image.png`, 1200×630)와 iOS 아이콘(`public/apple-touch-icon.png`)은 Figma 「디자인 시스템」 페이지의 `OG Image`·`Apple Touch Icon` 프레임에서 내보낸다. 웹 앱 매니페스트 아이콘(`public/icons/icon-192.png`·`icon-512.png`·`icon-maskable-512.png`·`badge-96.png`)은 같은 페이지의 PWA 아이콘 프레임에서 1x PNG로 내보낸다.
- **둥글기**: Figma `radius/md·lg·xl·full`(8·12·16·원형)은 Tailwind `rounded-lg·xl·2xl·full`에 대응한다. 입력·버튼 8, 메뉴 12, 카드·모달 16이다.
- **컴포넌트 대응**
  - Button → `components/Button.tsx`, `lib/ui.ts`의 `buttonClass`
  - Input → `lib/ui.ts`의 `inputClass`
  - Avatar → `components/Avatar.tsx`(Sm·Md·Lg·Xl, 사진을 불러오지 못하면 첫 글자)
  - Logo → `components/Logo.tsx`(`Logo`, `LogoMark`)
  - TopBar → `components/AppLayout.tsx`
  - Badge → `components/Badge.tsx`
  - OptionCard → `components/OptionCard.tsx`(투표 상세의 메뉴 카드, 결과 모드 포함). Figma `Link` 속성(식당 줄)을 켜면 「식당 이름 · 네이버 지도 ↗」(이름이 없으면 「지도 · 서비스 ↗」, `lib/link.ts serviceLabel`)·「식당 고치기」가 보이고, `Distance` 속성은 조직 위치에서의 거리·도보 시간(「350m · 도보 약 7분」)이다. 식당 찾기 모달(05-F, 「근처에서 찾기」·「링크 붙이기」 탭, 메뉴 입력과 카드의 고치기 공용)은 `components/PlaceModal.tsx`, 근처에서 찾기(검색·분류 칩·지도·목록·더 보기)는 `components/PlaceFinder.tsx`, 링크 붙이기 입력(찾기·공유 링크·이름·주소, 조직 위치도 공용)은 `components/PlaceFields.tsx`. `Comments` 속성은 카드 아래 「💬 댓글 N」 토글이다(댓글이 없으면 「댓글 달기」, 마감된 투표에서 댓글이 없으면 숨김, 카드 선택과 별개라 `stopPropagation`).
  - MenuComments(펼친 메뉴 댓글, Open·Readonly·Empty) → `components/OptionComments.tsx`. 카드(`role="button"`) 안에 입력창을 넣지 않도록 카드 밖 바로 아래에 그린다. 펼친 메뉴는 `PollDetailPage`가 들고 있어 마감돼도 펼친 채 읽기 전용으로 바뀐다. 관리자 콘솔도 `CommentList`·`CommentRow`를 쓴다
  - ChatMessage(Other·Mine·Deleted)·ChatPanel(Open·Closed) → `components/ChatPanel.tsx`(데스크톱 사이드 열 카드, 모바일 시트 안), 하단 「💬 채팅」 버튼·채팅 시트(05-C·05-C2) → `components/ChatSheet.tsx`. Chat Unread(UnreadBadge·ChatPreview·NewMessagesPill·UnreadDivider·ChatButton, 05-C4~C6·D05-C2·04-C) → `components/ChatUnread.tsx`(`UnreadBadge`, `ChatButton`, `ChatJumpButton`, `UnreadDivider`, `ListEnd`). 시트 안의 삭제 확인처럼 `<dialog>` 안에 `<dialog>`를 두면 React가 안쪽 `close`를 바깥 `onClose`로 올려 보내므로 `e.target === e.currentTarget`일 때만 닫는다
  - 투표 지도(05-G·D05-G) → `components/PollPlacesMap.tsx`(조직 위치·메뉴별 식당 핀, 모바일 접힘, 「⤢ 크게 보기」 → 큰 지도 모달 05-G2·D05-G2: 지도 + 목록, 목록을 누르면 지도가 그 식당으로 옮겨 간다), 지도 공용 → `components/NaverMap.tsx`
  - MapPin(지도 핀) → `NaverMap`의 `markerElement`: 물방울 핀 끝이 정확한 위치, 이름표는 핀 오른쪽. Tone(조직 위치·내 메뉴·그 밖), Number(식당 찾기 번호, 핀 머리), Show Label(식당 찾기는 고른 식당만 이름표)
  - 모달 크기: `Modal`의 `size="lg"`(폭 1024px, 큰 지도)와 `closable`(제목 옆 ✕). 기본은 448px. `Modal`은 `e.target === e.currentTarget`일 때만 `onClose`를 불러서, 프로필 모달 안의 방명록 지우기 확인·신고 창처럼 `<dialog>` 안에 둔 `ConfirmDialog`·`Modal`을 닫아도 바깥 모달은 그대로다
  - 멤버 카드(Figma 「멤버 N명」) → `components/MemberList.tsx`(조직 설정, 데스크톱 조직 홈 사이드)
  - 멤버 프로필 모달(07-P·07-P2) → `components/MemberProfileModal.tsx`(소개는 `useMembers` 캐시에서 찾고, 멤버가 아니면 이름·사진만). `OrgLayout`의 `ProfileViewerProvider`(`components/ProfileViewer.tsx`, 훅은 `hooks/useProfileViewer.ts`)가 모달을 하나만 두고, 사람을 누르는 곳은 `ProfileButton`을 쓴다(멤버 목록, `PersonChip`=참여자·미응답자 칩, 댓글·채팅 작성자). `ProfileButton`은 클릭·키 입력을 위로 올려 보내지 않아 메뉴 카드(`role="button"`) 안에서 눌러도 투표가 바뀌지 않고, 모달이 채팅 시트 밖에 있어 닫아도 시트는 그대로다. 조직 화면 밖(관리자 콘솔 등)에서는 글자로만 보인다. 사람이 바뀌면 탭이 처음으로 돌아가게 `MemberProfile`에 `key={person.userId}`를 준다
  - 방명록(07-P4~P5·03-N5) → `components/Guestbook.tsx`(`GuestbookPanel`: 목록·입력·쪽 넘기기·지우기 확인·신고, 주인이면 NEW와 `seen`), Pager(‹ 1 / 3 ›) → `components/Pager.tsx`, ProfileTabs → `MemberProfileModal`의 `ProfileTabs`, 방명록 신고(07-M2) → `components/GuestbookReportDialog.tsx`, 글 제한 경고(07-M4) → `components/GuestbookWarningDialog.tsx`, TopBar 「새 방명록 점」·ProfileMenu 배지 → `components/ProfileMenu.tsx`, 관리자 방명록 신고(A09b·DA09b) → `pages/admin/AdminReportsPage.tsx`의 `GuestbookReportRow`
  - 프로필 수정 모달(03-M2) → `components/ProfileModal.tsx`(사진·이름·한줄 소개·좋아하는 음식·상세 프로필을 「저장」 한 번에, 사진 → 이름 → 소개 → 상세 순서로 저장, 버튼 줄은 sticky), 상세 프로필 섹션 → `components/ProfileDetailsFields.tsx`, MbtiPicker·ChoicePill → `components/MbtiPicker.tsx`(줄마다 sr-only 라디오), Select → 네이티브 `<select>` + `inputClass`, TagInput·TagChip·SuggestChip → `components/TagInput.tsx`(Enter·쉼표·blur·「+」로 추가, 한글 조합 중 Enter는 무시, 더하지 않은 글도 저장에 넣는다, 추천 칩은 `ProfileDetailsFields`), 좋아하는 음식 입력 → `components/FoodTagInput.tsx`(TagInput, 10자·3개), ProfileDetailList → `components/ProfileDetailList.tsx`(마이페이지·멤버 프로필·관리자 회원 상세), 음식 배지 → `components/FoodTags.tsx`(Badge Brand), 사진 맞추기(03-M3) → `components/PhotoCropper.tsx`(계산은 `lib/photoCrop.ts`, 미리보기는 `data:` 주소: CSP가 `blob:` 이미지를 막는다)
  - 투표 관리 메뉴·모달(05-A, 05-M1~M3) → `components/PollManageMenu.tsx`. 제목·마감 시간 입력은 만들기(04-M)와 수정이 `components/PollForm.tsx`를 같이 쓴다.
  - StatCard·ListRow(관리자 콘솔) → `components/AdminParts.tsx`(`StatCard`, `ListRow`, `ActionRow`, `DangerZone`, `AdminSearch`)
  - TopBar 종 아이콘(「새 소식 점」) → `components/NoticeBell.tsx`, 새 소식 배너(04-B) → `components/NoticeBanner.tsx`, 새 소식 카드의 배지·본문 → `components/NoticeBadge.tsx`·`components/NoticeBody.tsx`
  - UpdateToast → `components/UpdateToast.tsx`
  - TopBar 「쪽지 배지」 → `components/LetterButton.tsx`(`UnreadBadge`), 쪽지함(10·D10) → `pages/LettersPage.tsx`(LetterItem 포함), AnonymousAvatar → `components/AnonymousAvatar.tsx`, 쪽지 쓰기(10-M1·M1b·M1c)와 「쪽지를 보냈어요」 안내 → `components/LetterComposer.tsx`(안내는 채팅 시트 같은 모달 위에서도 보이게 `popover`로 top layer에 올린다, 잠기지 않은 답장은 익명 체크 대신 안내), 쪽지 보기·지우기·차단 확인·신고(10-M2·M3·M5) → `components/LetterModal.tsx`(확인 창은 보기 모달 밖에 그린다), 차단한 사람 관리(10-M4) → `components/LetterBlocksModal.tsx`, 메시지 본문(채팅·쪽지 공용) → `components/MessageBody.tsx`, 관리자 신고(A09·DA09) → `pages/admin/AdminReportsPage.tsx`, 개요의 처리할 신고 카드 → `AdminParts`의 `StatCard`(`hint`)
  - 공지 글쓰기 모달(A08-M) → `components/NoticeFormModal.tsx`(쓰기·고치기 공용, 「미리보기」 토글)
  - Switch → `components/Switch.tsx`(`role="switch"`), PushSettingsCard(마이페이지 「알림」 카드 03-N6·03-N6b·D03-N6) → `components/NotificationSettings.tsx`(상태는 `hooks/usePushDevice.ts`)
  - 관리자 프로필 수정 모달(A03-M3) → `components/AdminProfileModal.tsx`(이름·한줄 소개·좋아하는 음식·상세 프로필, 사진은 없다). 입력은 `ProfileModal`(03-M2)과 같은 `FoodTagInput`·`ProfileDetailsFields`(관리자용 안내는 `description`)·`lib/profile.ts`·`lib/profileDetails.ts`를 쓰고, 「상세 프로필 지우기」 확인은 모달 안의 `ConfirmDialog`다
  - 이용 제한: RestrictionNotice(05-X·05-X2·10-X·03-N7) → `components/RestrictionNotice.tsx`(`hooks/useRestriction.ts`), RestrictionCard(03-N7·D03-N7) → `components/RestrictionCard.tsx`, SanctionNotice(00-S) → `components/SanctionNoticeDialog.tsx`(띄우는 순서는 `components/AppNoticeDialogs.tsx`), SanctionRow → `components/SanctionRow.tsx`(해제 확인 A03-M6 `LiftSanctionDialog` 포함), 제재하기(A03-M5) → `components/SanctionModal.tsx`, 「제재」 탭(A10·DA10) → `pages/admin/AdminSanctionsPage.tsx`, 문구·규칙 → `lib/sanctions.ts`
  - 사람 신고: 「이 사람 신고하기」(07-P6) → `MemberProfileModal`, 신고 창(07-M5) → `components/ReportUserDialog.tsx`, 신고 결과 창(ReportResult·00-R) → `components/ReportResultDialog.tsx`, 관리자 신고 · 프로필(A09c·DA09c) → `pages/admin/AdminReportsPage.tsx`의 `ProfileReportRow`
  - 랭킹(11·D11) → `pages/OrgRankingPage.tsx`(주간·월간과 기간은 주소 `?period=month&date=`, 해석은 `lib/ranking.ts parseRankingView`. 지금 기간은 `date` 없이 요청해 서버의 오늘로 정한다), PeriodSwitcher → `components/RankingPeriodSwitcher.tsx`, PodiumItem → `components/RankingPodium.tsx`(목록은 순위 순서이고 CSS `order`로 2위·1위·3위로 보인다), RankRow → `components/RankingList.tsx`, MyRankCard → `components/MyRankCard.tsx`(모바일은 화면 아래 fixed, 데스크톱은 사이드 맨 위), 채택 기록 모달(11-M) → `components/AdoptionModal.tsx`, 조직 홈 요약(04-R·D04-R) → `components/RankingTeaser.tsx`(`RankingHint` 모바일, `RankingCard` 데스크톱 사이드), AdoptedBadge·MakerBadge → `components/RankingBadges.tsx`(`OptionCard`의 `adopted`, `MemberList`·`MemberProfileModal`)

## 코드 스타일

- Java: 탭 들여쓰기(Spring Initializr 스타일), 주석은 한국어.
- 백엔드는 디버깅과 개발 편의를 위해 Lombok의 `@Slf4j`로 로깅한다. 로그에는 이메일 같은 개인정보 대신 ID를 남긴다.
- TypeScript: 2칸 들여쓰기, 세미콜론 없음, 작은따옴표, 로컬 import에 `.tsx`/`.ts` 확장자를 붙인다(`allowImportingTsExtensions`).
