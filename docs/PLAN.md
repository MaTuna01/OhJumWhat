# 오점왓 구현 계획

> 기획서: Notion 「점심메뉴 선정」 https://app.notion.com/p/3eb11d838f8d802e81dbfcd702c34692
> 이후 기획(PM 협업용으로 옮긴 곳): Notion https://app.notion.com/p/92bf482da589416c88ce1307aa8be911
> 작업 관리: 같은 페이지의 Tasks DB (단계별 작업, 선행/후속 관계 연결)
> 마지막 갱신: 2026-10-06 · 현재 버전: v1.11.0(변경 기록은 [CHANGELOG.md](../CHANGELOG.md)) · 운영: https://www.ohjumwhat.cloud (`main` push 시 자동 배포)

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
| 7 | 마무리·QA | 완료 — 404·오류 화면, 링크 미리보기, 탭 제목, CSP 등(v1.1.0~v1.1.1), 데스크톱 레이아웃(v1.2.0) | [#10](https://github.com/MaTuna01/OhJumWhat/pull/10), [#14](https://github.com/MaTuna01/OhJumWhat/pull/14), [#16](https://github.com/MaTuna01/OhJumWhat/pull/16) |
| - | 관리자 콘솔 (superadmin: 회원·조직·투표 관리, 강제 탈퇴·차단) | 완료(v1.3.0) | [#20](https://github.com/MaTuna01/OhJumWhat/pull/20) |
| 8 | 투표 조기 마감·수정·삭제 (확장 기능 1번째) | 완료(v1.4.0) | [#23](https://github.com/MaTuna01/OhJumWhat/pull/23) |
| 9 | 메뉴에 식당 지도 링크 (지도 연동 1단계) | 완료(v1.4.0) | [#24](https://github.com/MaTuna01/OhJumWhat/pull/24) |
| 10 | 메뉴 통계 (자주 먹은 메뉴, 최근 먹은 메뉴 제외 추천) | 완료(v1.4.0) | [#24](https://github.com/MaTuna01/OhJumWhat/pull/24) |
| - | 별명 (마이페이지 이름 바꾸기, 별명으로 활동) | 완료(v1.5.0) | [#27](https://github.com/MaTuna01/OhJumWhat/pull/27) |
| - | 지난 투표 기록 · 결과 복사(메신저 공유) | 완료(v1.5.0) | [#28](https://github.com/MaTuna01/OhJumWhat/pull/28) |
| 14 | 새 소식(공지사항): 업데이트(배포 때 자동 게시)·개발자 노트, 새 버전 안내 | 완료(v1.5.0) | [#29](https://github.com/MaTuna01/OhJumWhat/pull/29) |
| 11 | 식당 정보·네이버 지도 연동 1차: 공유 링크로 식당 붙이기(장소 정식 링크·식당 이름), 조직 위치 | 완료(v1.6.0) | [#32](https://github.com/MaTuna01/OhJumWhat/pull/32) |
| 15 | 식당 검색·네이버 지도 연동(지도 연동 3단계): 조직 주소·검색 반경, 투표 지도·거리(1차), 근처 식당 찾기·둘러보기·지난 식당(2차). 찾기는 카카오 로컬, 지도는 네이버 | 완료(v1.7.0) | [#41](https://github.com/MaTuna01/OhJumWhat/pull/41), [#42](https://github.com/MaTuna01/OhJumWhat/pull/42) |
| - | 투표별 채팅·메뉴 댓글(Notion 「14. 투표별 채팅/댓글 기능 추가」): 1단계 메뉴 댓글(진행 중에만 쓰기, 마감 뒤 읽기 전용, 관리자 삭제) → 2단계 투표 채팅(WebSocket, 마감 후 1시간까지) | 완료(v1.8.0) | [#55](https://github.com/MaTuna01/OhJumWhat/pull/55), [#58](https://github.com/MaTuna01/OhJumWhat/pull/58) |
| - | 프로필 사진: 직접 맞춰 올린 사진(서버 디스크 볼륨에 256px JPEG, DB와 함께 백업), 「프로필 수정」 모달(사진·이름 한 번에 저장), 관리자 「올린 사진 지우기」 | 완료(v1.8.0) | [#56](https://github.com/MaTuna01/OhJumWhat/pull/56) |
| - | 메뉴 선택 해제(Notion 「14. 메뉴 선택 해제 기능 추가」): 고른 메뉴 카드나 「오늘은 패스」를 다시 누르면 응답을 취소해 미응답으로 | 완료(v1.9.0) | [#65](https://github.com/MaTuna01/OhJumWhat/pull/65) |
| - | 프로필 한줄 소개·좋아하는 음식(Notion 「17. 프로필 항목 추가」): 마이페이지에서 정하고(빈 상태 안내), 멤버 목록·참여자 칩·댓글·채팅에서 사람을 누르면 멤버 프로필 모달 | 완료(v1.9.0) | [#67](https://github.com/MaTuna01/OhJumWhat/pull/67) |
| 18 | 채팅 안 읽은 메시지 알림(Notion 「18. 채팅에 메시지가 오면 알림」): 읽은 위치를 서버에 저장, 채팅 버튼 빨간 배지·도착 미리보기, 「여기부터 새 메시지」·「새 메시지 N ↓」, 데스크톱 화면 밖 떠 있는 버튼, 조직 홈 카드 「💬 N」 | 완료(v1.9.0) | [#69](https://github.com/MaTuna01/OhJumWhat/pull/69) |
| 22 | 상세 프로필(Notion 「22. 프로필 항목 추가」): MBTI·퍼스널컬러·취미·나이·직급. 「프로필 수정」에서 다섯 항목 모두 필수(지우기 없음), 마이페이지·멤버 프로필·관리자 회원 상세에 보인다 | 완료(v1.10.0) | [#75](https://github.com/MaTuna01/OhJumWhat/pull/75) |
| 21 | 쪽지(Notion 「21. 같은 조직에 추가된 사용자들끼리 쪽지 주고받기 기능」): 같은 조직 멤버끼리 한 통씩 주고받기, 익명·익명 쪽지에도 답장, 받은 사람의 차단(실명은 사람, 익명은 쪽지 한 통 단위)·신고, 관리자 「신고」 탭, 상단 바 안 읽은 쪽지 배지 | 완료(v1.10.0) | [#76](https://github.com/MaTuna01/OhJumWhat/pull/76), 익명 보호 수정 [#78](https://github.com/MaTuna01/OhJumWhat/pull/78) |
| - | 메뉴 채택 랭킹(메뉴 메이커, Notion 「15. 메뉴 채택 랭킹 기능 추가」): 마감된 투표마다 가장 많이 고른 메뉴(30% 이상)를 제안한 사람에게 1회, 조직 「랭킹」 탭(주간·월간, 12개월 전까지, TOP3 포디움·채택 기록), 조직 홈 이번 주 TOP3, 결과 화면 「👑 채택」, 지난달 1위 「이달의 메뉴 메이커」 배지. 스키마 변경 없음 | 완료(v1.11.0) | [#88](https://github.com/MaTuna01/OhJumWhat/pull/88) |
| 20 | 방명록(Notion 「20. 방명록 기능 추가」): 사람마다 방명록 하나(조직을 같이 쓰는 사람이 쓰고 봄), 100자 한 줄·5초에 한 번, 10개씩 쪽 넘기기, 쓴 사람·주인 지우기, 주인만 신고 → 관리자 「글 제한」·「문제 없음」, 상단 바 새 방명록 점·마이페이지 NEW·글 제한 경고. 1차는 화면 안 알림, FCM 웹 푸시(방명록·쪽지)는 2차 | 1차 완료(v1.11.0) | [#92](https://github.com/MaTuna01/OhJumWhat/pull/92) |
| - | 관리자 회원 프로필 수정·지우기: 회원 상세 「프로필 수정」에서 별명(비우면 구글 이름)·한줄 소개·좋아하는 음식·상세 프로필을 고치고, 「상세 프로필 지우기」로 다섯 항목을 한꺼번에 지운다. 규칙·문구는 마이페이지와 같고 본인에게 알리지 않는다(다른 관리자·자기 자신도 된다). 스키마 변경 없음 | 완료(v1.11.0) | [#94](https://github.com/MaTuna01/OhJumWhat/pull/94), 별명 되쓰기 수정 [#96](https://github.com/MaTuna01/OhJumWhat/pull/96) |
| 20 | 방명록 2차: FCM 웹 푸시(새 방명록·글 제한 경고·새 쪽지) | 다음 단계 | - |
| 19 | 채팅에서 사진 전송(Notion 「19. 채팅에서 사진 전송 기능 추가」) | 시작 전 | - |
| 12 | 중복 투표(관심 표시 후 최종 한 곳 확정) | 시작 전 | - |
| 13 | 최소 인원 미달 메뉴 자동 해산 후 재선택(12 다음) | 시작 전 | - |
| - | 랭킹 「메뉴 연주자」(연속 3회 채택) 칭호 | 보류 — '연속'의 정의를 PM과 정한 뒤 | - |

확장 기능(8~13단계)의 순서와 체크리스트는 Notion Tasks에 있다. 기획서 「나중에」 목록을 구현 난이도 순으로 정렬했다: 8 투표 조기 마감·수정·삭제 → 9 메뉴에 식당 지도 링크 → 10 메뉴 통계 → 11 식당 정보·지도 연동(검색 API는 약관상 결과를 저장할 수 없어 네이버 공유 링크 방식으로, 결과 지도는 15단계에서) → 12 중복 투표 → 13 최소 인원 미달 자동 해산. 14 공지사항(새 소식)은 배포마다 바뀐 점을 알리려고 나중에 추가했고, 그 뒤 기능(채팅·프로필·쪽지·랭킹·방명록 등)은 사용 피드백과 PM 기획으로 정했다. 다음 단계의 자세한 내용은 아래 「다음 단계」에 있다.

## 계획을 세운 뒤 정한 것
- 메뉴를 추가해도 추가한 사람이 **자동으로 참여하지 않는다**. 추가와 참여는 따로 한다.
- Java는 **21 LTS**를 쓴다(개발 PC에 21이 설치되어 있어서). Spring Boot 4.1.1, PostgreSQL 18, Node 24.
- 디렉터리 이름은 IDE에서 구분하기 쉽게 `ohjumwhat-backend/`, `ohjumwhat-frontend/`로 한다. Java 패키지는 `com.ohjumwhat`이다.
- 세션은 **Spring Session JDBC로 DB에 저장**한다. 그래서 재시작·재배포 후에도 로그인이 유지되고, `SESSION` 쿠키는 30일 유효하다. 로그인하지 않은 요청에는 세션을 만들지 않는다(`NullRequestCache`).
- DB의 DATE·TIME 값이 시간대 변환으로 틀어지는 문제가 있어 `hibernate.jdbc.time_zone` 설정을 제거했다(5단계에서 발견). 이제 한국 기준 값이 그대로 저장된다.
- 백엔드 로깅은 Lombok `@Slf4j`로 한다. 로그에는 개인정보 대신 ID를 남긴다.
- 화면·디자인 시스템은 Figma(https://www.figma.com/design/w0OIV1khSVnxlf5KfRo6aP/OhJumWhat)가 기준이다. 글꼴은 Noto Sans KR이고, 색은 의미 기반 토큰(`bg-bg-*`, `text-text-*`, `border-border-*`)만 쓴다. 자세한 규칙은 CLAUDE.md 「디자인 시스템」에 있다.
- 투표 현황은 폴링(진행 중 3초)으로 충분하다고 보고 WebSocket을 쓰지 않는다. 투표 채팅(v1.8.0)만 받기를 WebSocket으로 하고, 보내기·고치기·지우기는 REST다.

## 형상관리
- 원격 저장소: https://github.com/MaTuna01/OhJumWhat
- 작업 흐름(이슈 #48부터, 자세한 규칙은 CLAUDE.md 「작업 규칙」)
  - 착수 전에 GitHub 이슈(`[종류] 요약`, 양식 `.github/ISSUE_TEMPLATE/task.md`)를 만든다.
  - `dev`의 최신 상태에서 `<종류>/<이름>` 브랜치로 분기한다. 종류는 `feat`·`fix`·`refactor`·`docs`·`test`·`chore`·`release`다. 그 전 브랜치는 `feature/<기능명>`이었다.
  - 커밋 메시지 첫 줄은 `[#이슈 번호] - 요약`이다. PR은 `dev`로 열고, 제목도 같은 형식이며 본문 첫 줄에 `이슈: #N`을 적는다.
  - `dev` 머지는 테스트·빌드·동작 확인 뒤 개발자가 직접 한다(merge commit, 제목 `[#N] - 요약 (#PR)`). 머지하면 이슈를 닫는다(`dev`는 기본 브랜치가 아니라 `Closes #N`으로 닫히지 않는다). 작업이 끝나면 브랜치를 정리한다.
- `dev` → `main` 승격(릴리스)은 사용자가 결정한다. 승격 하나가 릴리스 하나이고, `main`에 push하면 자동으로 배포된다. 버전·`CHANGELOG.md`·업데이트 글 준비, 백업, 태그·GitHub Release 순서는 `docs/DEPLOY.md` 「릴리스와 버전」에 있다.
- git에 올리지 않는 파일: `.env`, `ohjumwhat-backend/src/main/resources/application.yml`, 구글 OAuth 클라이언트 JSON(`client_secret_*.json`), 서버 pem 키. 저장소에는 예시 파일(`.env.example`, `deploy/.env.example`, `application.example.yml`)만 커밋한다. 실제 값은 Notion 「개발 필요 파일」 페이지에 있다.

## 저장소 구조
```
ohjumwhat/
├─ ohjumwhat-backend/        Spring Boot 4 (Gradle Kotlin DSL, Java 21)
├─ ohjumwhat-frontend/       Vite + React 19 + TS + React Router + TanStack Query + Tailwind v4
├─ docs/PLAN.md              이 문서
├─ docs/DEPLOY.md            서버 설정, Secrets, 롤백, 백업·복원, 릴리스 절차
├─ CHANGELOG.md              버전별 변경 기록(개발자용)
├─ docker-compose.dev.yml    로컬 PostgreSQL
├─ .env.example              (.env는 gitignore)
├─ .claude/launch.json       Claude 미리보기용 dev 서버 설정(프론트·백엔드)
├─ CLAUDE.md / README.md
├─ Dockerfile                프론트 빌드 → Spring Boot static → bootJar → JRE 이미지
├─ deploy/                   운영 compose(Caddy + app + PostgreSQL), Caddyfile(HTTPS·CSP), backup.sh
└─ .github/                  workflows(ci.yml, deploy.yml), 이슈 양식
```

## 백엔드 설계
**패키지**(도메인별)
- `auth`: 보안 설정, 구글 로그인, `LoginUser`, 관리자 권한 확인(`AdminAuthorizationManager`)
- `user`: 사용자, `/api/me`, 별명·프로필 사진·한줄 소개·상세 프로필, 차단된 계정
- `organization`: 조직, 멤버십, 초대, 조직 위치
- `poll`, `menu`, `vote`: 투표, 메뉴 항목·메뉴 댓글·메뉴 통계, 참여
- `schedule`: 정기 투표 규칙, 매분 정기 투표 열기(`PollScheduler` → `ScheduledPollOpener`)
- `place`: 식당 링크 규칙(`PlaceLinks`·naver.me 확인), 지도·근처 식당 찾기(카카오 로컬, `/api/orgs/{id}/places`)
- `chat`: 투표 채팅(보내기는 REST, 받기는 WebSocket `ChatHub`), 안 읽은 메시지, 30초마다 연결 정리(`ChatSweeper`)
- `letter`: 쪽지(익명·답장·차단·신고)
- `guestbook`: 방명록(쓰기·지우기·신고, 화면 안 알림)
- `ranking`: 메뉴 채택 랭킹(채택 규칙 `MenuAdoption`, 기간 `RankingPeriod`)
- `notice`: 새 소식(업데이트 글 파일 동기화 `ReleaseNoteSync`, 개발자 노트)
- `admin`: 관리자 콘솔(회원·조직·투표·차단·신고·공지, 강제 탈퇴)
- `common`: `Clock`, 공통 예외 처리, SPA 포워딩, 화면 설정(`/api/config`), 사용자 글 정리(`UserText`), 속도 제한(`SlidingWindowRateLimiter`)

**스키마**(Flyway가 원본, JPA는 `ddl-auto: validate`)
- V1: ERD의 테이블 7개
- V2: `spring_session` 테이블
- V3: 관리자(회원 역할·최근 로그인, 차단 목록, 메뉴 작성자 NULL 허용)
- V4: `menu_options.link_url`(식당 지도 링크)
- V5: `users.nickname`(별명)
- V6: `notices`(새 소식: 업데이트·개발자 노트, 게시 시각), `users.notices_seen_at`(새 소식을 마지막으로 본 시각)
- V7: `menu_options.place_name`(식당 이름), `organizations.area`·`office_name`·`office_link_url`(조직 위치)
- V8: `menu_options.place_address`(식당 주소), `organizations.office_address`·`search_radius`(조직 주소, 검색 반경 500·1000·2000m). 좌표는 약관상 저장하지 않는다
- V9: `menu_options.kakao_place_id`·`place_query`(근처 식당 찾기로 고른 카카오 식당: 장소 ID와 검색어만, 이름·위치는 볼 때 다시 찾는다)
- V10: `users.photo_key`(올린 프로필 사진의 파일 키. 파일은 서버 디스크 `ohjumwhat.photos.dir`의 `{key}.jpg`)
- V11: `menu_comments`(메뉴 댓글. 메뉴를 지우면 함께 지우고, 회원이 지워지면 작성자만 NULL)
- V12: `chat_messages`(투표 채팅. 지운 메시지는 행을 남기고 본문만 비운다)
- V13: `users.bio`·`food_tags`(한줄 소개·좋아하는 음식)
- V14: `chat_reads`(투표 채팅의 읽은 위치, (투표, 사람)당 한 행)
- V15: `users.mbti`·`personal_color`·`hobbies`·`age`·`job_title`(상세 프로필, CHECK `ck_users_profile_details`: 모두 비었거나 모두 채워졌거나)
- V16: `letters`·`letter_blocks`·`letter_reports`(쪽지. 사용자는 쪽마다 소프트 삭제)
- V17: 쪽지 차단 유일 제약을 실명(사람 단위)·익명(쪽지 한 통 단위)으로 나눔, 차단 대상 FK `SET NULL`
- V18: `letters.recipient_id` `SET NULL`(받은 사람이 강제 탈퇴해도 쪽지·신고를 남긴다)
- V19: `guestbook_entries`·`guestbook_reports`(방명록, 소프트 삭제), `users.guestbook_seen_at`·`guestbook_warnings_seen_at`
- UNIQUE: memberships(org, user), votes(poll, user), polls(schedule_id, poll_date), menu_options(poll_id, name)
- CHECK: close_time > open_time, closes_at > opens_at. 인덱스: polls(organization_id, poll_date)
- FK
  - organization_id를 참조하는 FK와 polls를 참조하는 FK는 모두 `ON DELETE CASCADE`다. 단 `letters.organization_id`는 `SET NULL`이다(조직이 없어져도 쪽지를 남긴다, 「삭제된 조직」).
  - 글을 쓴 사람을 가리키는 FK(메뉴·댓글·채팅·쪽지·방명록의 작성자, 쪽지 받은 사람·방명록 주인)는 `SET NULL`이다. 강제 탈퇴해도 글은 「탈퇴한 사용자」로 남는다. 응답(votes)은 CASCADE로 지워진다.
  - polls.schedule_id는 `SET NULL`이다.
  - votes.option_id는 **`NO ACTION`**이다. RESTRICT로 두면 poll이 CASCADE로 지워질 때 즉시 검사에 걸리므로, 문장 끝에 검사하는 NO ACTION을 쓴다. 메뉴 삭제 가능 여부는 서비스에서 먼저 확인한다.
- 로그인은 회원 행 전체를 다시 쓴다. 그래서 사용자가 바꾸는 `users` 컬럼(별명·사진 키·소개·상세 프로필·새 소식/방명록 시각)은 엔티티 저장에서 빼고(`insertable/updatable=false`) `UserRepository`의 update 쿼리로만 바꾼다.

**시간**: 모든 계산은 `Clock` 빈(Asia/Seoul)으로 한다. "오늘"(`poll_date`), 마감 판정, 정기 투표 시각, 채팅 기간, 랭킹 기간이 여기에 해당한다. 서비스 코드에서 `now()`를 직접 부르지 않는다.

**인증**
- 구글 OIDC로 로그인하고, google_sub 기준으로 users를 upsert한다. 세션 principal은 `LoginUser`(users.id 포함)다. 차단된 구글 계정은 로그인을 거절한다.
- 로그인하지 않은 `/api/**` 요청에는 401을 준다. CSRF는 `csrf.spa()` 방식이고, `CsrfCookieFilter`가 매 응답에 `XSRF-TOKEN` 쿠키를 내린다.
- 관리자 API(`/api/admin/**`)는 요청마다 DB의 role로 확인한다(아니면 403). 관리자는 설정 `ADMIN_EMAILS`로만 지정한다.
- 로그인 후에는 항상 `/`로 보내고, 이동할 곳은 프론트가 정한다. 로그아웃은 `POST /logout`(204)이다.
- 파일이 없는 화면 경로는 `index.html`로 돌려준다(`SpaWebConfig`).

**API 규칙**
- 사용자에게 보여줄 오류는 `ApiException`으로 던지고, `{"message": "..."}`로 응답한다.
- 조직 하위 API는 `MembershipService.requireMember`로 먼저 확인한다. 멤버가 아니면 404다.
- 투표 쓰기 API(메뉴·참여)는 최신 투표 상세를 돌려주고, 프론트는 이것을 바로 캐시에 넣는다. 동시 요청이 DB 제약에 걸리면 409다.

**REST API**

| 영역 | 엔드포인트 | 상태 |
|---|---|---|
| 나 | `GET /api/me` (프로필 + lastVisitedOrgId, name은 별명 또는 구글 이름), `PUT /api/me/nickname` (별명, 비우면 구글 이름), `PUT /api/me/profile` (한줄 소개·좋아하는 음식, 통째로), `PUT /api/me/profile/details` (상세 프로필, 다섯 항목 모두), `POST·DELETE /api/me/photo` (프로필 사진 올리기·구글 사진으로 되돌리기), `GET /api/photos/{key}.jpg` (올린 사진, 로그인 필요·긴 캐시), `GET /api/me/orgs` (이름·멤버 수·오늘 진행 중인 투표 여부) | 완료 |
| 조직 | `POST /api/orgs`, `GET /api/orgs/{id}` (last_visited_at 갱신), `PATCH /api/orgs/{id}`, `PUT /api/orgs/{id}/location` (검색 지역·조직 위치·조직 주소·검색 반경), `GET /api/orgs/{id}/members`, `DELETE /api/orgs/{id}/membership` | 완료 |
| 초대 | `GET /api/invites/{token}`, `POST /api/invites/{token}/join` | 완료 |
| 투표 | `GET /api/orgs/{id}/polls/today`, `GET /api/orgs/{id}/polls/history?page=` (지난 투표, 10개씩), `POST /api/orgs/{id}/polls` (title, closesAt "HH:mm"), `GET /api/orgs/{id}/polls/{pollId}` (상세 집계) | 완료 |
| 투표 관리 | `PUT /api/polls/{pollId}` (title, closesAt "HH:mm"), `POST /api/polls/{pollId}/close` (지금 마감), `DELETE /api/polls/{pollId}` (수동 투표만) — 진행 중일 때 멤버 누구나 | 완료 |
| 메뉴 | `POST /api/polls/{pollId}/options` (name, 식당 선택: link·placeName·placeAddress 또는 kakaoPlaceId·placeQuery), `DELETE /api/polls/{pollId}/options/{optionId}`, `PUT /api/polls/{pollId}/options/{optionId}/link` (같은 식당 값), `GET /api/orgs/{id}/menu-names?q=` (자동완성: 이름 + 마지막으로 먹은 날 + 지난번 식당) | 완료 |
| 메뉴 댓글 | `GET/POST /api/polls/{pollId}/options/{optionId}/comments`, `PUT/DELETE …/comments/{commentId}` (진행 중에만 쓰기, 고치기·지우기는 쓴 사람만, 200자) | 완료 |
| 통계 | `GET /api/orgs/{id}/menu-stats?days=` (없으면 전체), `GET /api/orgs/{id}/menu-recommendations` | 완료 |
| 랭킹 | `GET /api/orgs/{id}/ranking?period=week\|month&date=YYYY-MM-DD` (그 날짜가 들어 있는 주·달, 없으면 오늘. 12개월 전보다 앞이나 앞으로의 날짜는 400). 투표 상세의 `adoption`(마감된 투표의 채택 메뉴) | 완료 |
| 지도 | `GET /api/config` (네이버 지도 키, 위치 찾기 가능 여부), `GET /api/orgs/{id}/places?optionIds=` (조직·식당 좌표, 카카오 식당은 이름까지, 볼 때마다 찾음, 30개까지), `GET /api/orgs/{id}/places/search?q=` (근처 식당 찾기, 45개까지, 이름·분류가 맞는 곳이 앞) | 완료 |
| 참여 | `PUT /api/polls/{pollId}/vote` `{optionId: number \| null}` (null이면 "오늘은 패스"), `DELETE /api/polls/{pollId}/vote` (응답 취소 → 미응답, 진행 중에만) | 완료 |
| 채팅 | `GET /api/polls/{pollId}/messages?before=` (50개씩), `POST …/messages`, `PUT/DELETE …/messages/{id}` (투표 오픈 ~ 마감 + 1시간, 300자, 한 사람 10초 10개), `PUT …/messages/read {lastReadId}` (읽은 위치), 받기는 WebSocket `/api/polls/{pollId}/ws` | 완료 |
| 정기 | `GET/POST /api/orgs/{id}/schedules`, `PUT/DELETE /api/orgs/{id}/schedules/{sid}` | 완료 |
| 새 소식 | `GET /api/notices?page=` (최신순 10개씩, 항목마다 unread), `GET /api/notices/unread` (안 읽은 수·가장 최근 것), `POST /api/notices/seen` | 완료 |
| 쪽지 | `GET /api/letters?box=received\|sent&before=` (20통씩), `GET /api/letters/unread`, `POST /api/letters` (같은 조직 멤버에게, 500자, 10분에 10통), `POST /api/letters/{id}/reply`, `PUT /api/letters/{id}/read`, `DELETE /api/letters/{id}` (내 쪽에서만), `POST /api/letters/{id}/block`·`report`, `GET /api/letters/blocks`, `DELETE /api/letters/blocks/{id}` | 완료 |
| 방명록 | `GET /api/guestbook/users/{ownerId}?page=` (10개씩 최신순), `POST /api/guestbook/users/{ownerId}` (100자, 5초에 한 번), `DELETE /api/guestbook/entries/{id}`, `POST /api/guestbook/entries/{id}/report` (주인만), `GET /api/guestbook/alerts`, `POST /api/guestbook/seen`, `POST /api/guestbook/warnings/ack` | 완료 |
| 관리자 | `GET /api/admin/stats`, `GET /api/admin/users?q=`, `GET/DELETE /api/admin/users/{id}` (상세·강제 탈퇴), `GET /api/admin/blocks`, `DELETE /api/admin/blocks/{id}` (차단 풀기), `GET /api/admin/orgs?q=`, `GET/DELETE /api/admin/orgs/{id}`, `DELETE /api/admin/orgs/{id}/members/{userId}`, `GET/DELETE /api/admin/polls/{id}` (정기 규칙도 함께 지우기 `withSchedule`), `DELETE /api/admin/menu-options/{id}`, `DELETE /api/admin/schedules/{id}` | 완료 |
| 관리자 사진 | `DELETE /api/admin/users/{id}/photo` (올린 프로필 사진 지우기, 구글 사진으로) | 완료 |
| 관리자 프로필 | `PUT /api/admin/users/{id}/nickname` (별명, 비우면 구글 이름), `PUT /api/admin/users/{id}/profile` (한줄 소개·좋아하는 음식, 통째로), `PUT /api/admin/users/{id}/profile/details` (상세 프로필, 다섯 항목 모두), `DELETE /api/admin/users/{id}/profile/details` (다섯 항목 한꺼번에 지우기) — 모두 회원 상세를 돌려주고 마이페이지와 같은 규칙·문구 | 완료 |
| 관리자 댓글·채팅 | `GET /api/admin/menu-options/{id}/comments`, `DELETE /api/admin/menu-comments/{id}`, `GET /api/admin/polls/{id}/messages`, `DELETE /api/admin/chat-messages/{id}` (마감·채팅 기간과 상관없이) | 완료 |
| 관리자 공지 | `POST /api/admin/notices`, `PUT/DELETE /api/admin/notices/{id}` (개발자 노트만, 업데이트 글은 409) | 완료 |
| 관리자 쪽지 신고 | `GET /api/admin/letter-reports?status=open\|all` (익명이어도 실제 보낸 사람), `POST /api/admin/letter-reports/{id}/resolve` | 완료 |
| 관리자 방명록 신고 | `GET /api/admin/guestbook-reports?status=open\|all`, `POST /api/admin/guestbook-reports/{id}/restrict`·`dismiss` | 완료 |

**핵심 규칙**
- **투표 상세 응답**(폴링 대상): 한 번 호출로 화면 전체를 그릴 수 있게 한다. 투표 정보(`id`·`title`·`opensAt`·`closesAt`·`scheduled`·`memberCount` 등) 외에 담는 값은 `status`(OPEN/CLOSED, now ≥ closesAt이면 CLOSED), `chatClosesAt`, `options[{id, name, 식당(link·placeName·placeAddress·kakaoPlaceId·placeQuery), createdBy, voters[], mine, deletable, commentCount}]`, `myResponse`·`myOptionId`, `passed[]`, `nonRespondents[]`, `soloOptionIds[]`, `adoption`(마감된 투표의 채택 메뉴)이다.
- **참여 변경**: votes(poll, user)를 upsert하고 option_id만 바꾼다. 응답 취소는 행을 지워 미응답으로 돌린다. 마감된 투표면 409, 다른 투표의 option이면 400이다.
- **메뉴 추가**: 앞뒤 공백을 지우고, 빈 값은 거부한다. 같은 이름이 있거나 마감된 투표면 409다. 자동 참여는 하지 않는다.
- **메뉴 삭제**: 추가한 사람만, 참여자가 0명이고 투표가 진행 중일 때 할 수 있다. 조건이 안 맞으면 403 또는 409다.
- **수동 투표 생성**: opens_at=now, poll_date=오늘(KST)로 만든다. closesAt은 now보다 뒤여야 한다.
- **탈퇴**: 조직 행을 비관적 락으로 잡는다. 진행 중인 투표의 내 votes를 지우고 membership을 지운다. 남은 멤버가 0명이면 조직을 삭제한다(나머지는 CASCADE).
- **정기 투표 스케줄러**: `@Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")`로 1분마다 돈다. 오늘 요일 비트가 켜져 있고 `open_time ≤ now < close_time`인데 (schedule, today)로 만든 poll이 없으면 새로 만든다(제목은 규칙 이름). UNIQUE 위반은 무시한다. 그래서 중복 생성이 막히고, 재시작으로 놓친 투표도 다음 실행 때 만들어진다.
- 채팅·쪽지·방명록·랭킹 등 나머지 도메인 규칙은 CLAUDE.md 「아키텍처」의 「도메인 규칙 중 코드만 봐서는 알기 어려운 것」에 있다.

## 프론트엔드 설계
- **라우트**(`src/router.tsx`)
  - `/login`, `/`(진입 분기), `/invite/:token`, `/me`, `/notices`, `/letters`
  - `/orgs/:orgId`(`OrgLayout` 아래: 홈, `polls/:pollId`, `schedules`, `stats`, `ranking`, `settings`)
  - `/admin`(`AdminLayout` 아래: 개요, `users`, `users/:userId`, `orgs`, `orgs/:orgId`, `polls/:pollId`, `blocks`, `notices`, `reports`)
  - 없는 경로는 `NotFoundPage`, 렌더링 오류는 `RouteErrorPage`다.
- **진입 분기**: 기억해 둔 경로(초대 링크) → 최근 조직 → `/me`. 로그인이 필요한 화면은 `RequireAuth`로 감싼다.
- **API**: `lib/api.ts`의 `api()`로만 호출한다(XSRF 헤더, 에러 메시지). 서버 상태 훅과 query key는 `src/queries/`에 둔다.
- **화면 갱신**
  - 투표 상세는 진행 중일 때 3초마다 폴링하고, 마감되면 멈춘다. 참여는 `applyVote`로 먼저 반영하는 낙관적 업데이트다.
  - 투표 채팅만 WebSocket으로 받는다(탭이 보일 때만 연결, 끊기면 다시 연결하고 놓친 메시지를 다시 받는다).
  - 조직 홈은 진행 중이거나 채팅이 열린 투표가 있으면 15초마다, 쪽지 안 읽은 수는 30초마다·창으로 돌아올 때, 방명록 알림은 60초마다, 새 버전 확인(`/version.json`)은 5분마다·창으로 돌아올 때 다시 받는다.
- **공통 UI**
  - 상단 바: 로고, 조직 전환, 쪽지 봉투 배지(`LetterButton`), 새 소식 종(`NoticeBell`), 프로필 메뉴(새 방명록 점)
  - `Modal`·`ConfirmDialog`(네이티브 dialog), `lib/ui.ts`(버튼·입력창 스타일), 1024px 이상에서 본문 + 오른쪽 사이드 2단
  - Figma 컴포넌트와 코드 파일의 대응은 CLAUDE.md 「디자인 시스템」에 있다.

## 다음 단계
아래 순서대로 한다(진행 현황 표의 예정 행과 같은 순서).

- **방명록 2차 — 웹 푸시(FCM)**: 새 방명록·글 제한 경고·새 쪽지를 알린다. 방명록은 1차가 트랜잭션 안에서 발행하는 `GuestbookEntryCreatedEvent`·`GuestbookEntryRestrictedEvent`(ID만, 본문 없음)를 커밋 뒤에 받고, 새 쪽지 이벤트는 2차에서 새로 만든다. 새 외부 리소스라 운영 CSP(`deploy/Caddyfile`)도 함께 고친다.
- **19. 채팅에서 사진 전송**: Notion Tasks 「시작 전」.
- **12. 중복 투표(관심 표시 후 최종 한 곳 확정)**: Notion Tasks 「시작 전」. 지금은 votes가 (poll, user)당 한 행이라 참여 규칙(서버 `PollService.detail`·화면 `applyVote`)과 스키마가 함께 바뀐다.
- **13. 최소 인원 미달 메뉴 자동 해산 후 재선택**: Notion Tasks 「시작 전」, 12 다음에 한다.
- **랭킹 「메뉴 연주자」 칭호**: 연속 3회 채택. '연속'의 정의를 PM과 정한 뒤 2차로 한다.

## 테스트 전략
- **백엔드**: 통합 테스트(`IntegrationTest` 상속, Testcontainers PostgreSQL, 테스트마다 TRUNCATE)
  - 로그인은 `TestAuth.loginAs`, CSRF는 `TestAuth.xsrf`로 만든다.
  - 시간에 따라 달라지는 동작은 `TestClock`(`clock.set(...)`, 한국 시간)으로 고정한다.
  - 백그라운드 작업(정기 투표 열기, 채팅 연결 정리)은 `ohjumwhat.scheduler.enabled=false`로 끄고 직접 부른다.
  - 외부 API(카카오 로컬, naver.me)는 Fake 설정이 대신한다. 업데이트 글 파일 형식은 `ReleaseNoteTest`가 확인한다.
- **프론트**: Vitest로 유틸·규칙(`src/lib/`)과 진입 분기 로직을 테스트한다.
- v1.11.0 기준 백엔드 329개, 프론트 171개다.
- CI(`ci.yml`, `dev`·`main`으로 가는 PR, `dev` push, `main` 배포 전): `./gradlew test`, `npm run lint`, `npm test`, `npm run build`

## 검증 (end-to-end)
1. 실행: `docker compose -f docker-compose.dev.yml up -d` → `./gradlew bootRun` → `npm run dev` → http://localhost:5173
2. 구글 로그인 → 조직 생성 → 초대 링크 참여
3. 투표 생성 → 메뉴 추가·자동완성 → 참여·변경·패스·취소. 3초 안에 반영되는지, 미응답자와 1인 메뉴가 표시되는지 확인한다.
4. 마감 시간이 지나면 결과 모드로 바뀌고 수정이 막히는지 확인한다.
5. 현재 시각+1분을 오픈 시간으로 하는 정기 투표를 만들고 자동 생성되는지 확인한다.
6. 마지막 멤버가 탈퇴하면 조직이 삭제되는지 확인한다.
7. 배포 후 운영 도메인에서 HTTPS·로그인·폴링·채팅 연결을 확인하고, `/version.json`이 배포한 버전인지 본다. CSP는 https로 확인한다(CLAUDE.md 「배포」).
