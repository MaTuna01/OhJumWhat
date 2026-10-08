# 오점왓 배포 가이드

운영 환경은 가비아 클라우드 서버 한 대에서 Docker Compose로 **Caddy(HTTPS) → 앱 → PostgreSQL**을 실행한다. `main`에 push하면 GitHub Actions가 테스트 → 이미지 빌드(GHCR) → 서버 배포를 자동으로 한다.

| 항목 | 값 |
|---|---|
| 서버 공인 IP | `1.201.114.178` |
| 도메인 | `www.ohjumwhat.cloud` (루트 `ohjumwhat.cloud`는 www로 리디렉션) |
| 이미지 | `ghcr.io/matuna01/ohjumwhat:latest` (커밋별 태그 `:<sha>`도 함께 올라간다) |
| 서버 배포 폴더 | `~/ohjumwhat` (`docker-compose.yml`, `Caddyfile`, `backup.sh`는 배포 때마다 덮어쓰고, `.env`는 서버에만 둔다) |

## 구성 파일

| 파일 | 역할 |
|---|---|
| `Dockerfile` | 프론트 빌드 → `static/`에 복사 → Spring Boot jar → JRE 21 이미지(비루트 사용자, `MaxRAMPercentage=50`, 프로필 사진 폴더 `/data/photos`) |
| `deploy/docker-compose.yml` | 운영 스택. DB는 외부 포트를 열지 않고, 80·443은 Caddy만 연다. 앱의 관리 포트(8081, 헬스·지표)는 서버의 127.0.0.1에만 연다(아래 「모니터링」). 볼륨은 `db-data`(DB), `photos`(올린 프로필 사진), `caddy-*`(인증서). 컨테이너 로그는 서비스마다 10MB × 5개까지만 둔다 |
| `deploy/Caddyfile` | 인증서 자동 발급·갱신, 압축, 보안 헤더(CSP 등), 루트 도메인 → www 리디렉션. 바뀌면 배포 때 검증한 뒤 Caddy 컨테이너를 다시 만든다(파일 하나를 마운트해서 `up -d`만으로는 반영되지 않는다). |
| `deploy/backup.sh` | `pg_dump`와 프로필 사진 폴더(tar.gz)의 일일 백업(14일 보관). `.env`를 셸로 읽지 않고 db 컨테이너의 환경변수(`POSTGRES_USER`·`POSTGRES_DB`)로 덤프한다. 실패하면 쓰던 파일을 지우고 「백업 실패」를 남긴다 |
| `deploy/.env.example` | 서버 `.env` 템플릿 |
| `.github/workflows/ci.yml` | PR과 `dev` push에서 백엔드 테스트, 프론트 린트·테스트·빌드 |
| `.github/workflows/deploy.yml` | `main` push(또는 수동 실행) 시 CI → 이미지 → 배포 → 헬스 체크(`/healthz`, 2분) |

## 처음 한 번 해야 할 일

### 1. DNS (가비아 → 도메인 관리 → DNS 설정)

| 호스트 | 타입 | 값 |
|---|---|---|
| `www` | A | `1.201.114.178` |
| `@` | A | `1.201.114.178` |

도메인을 새로 등록했다면 `.site` 최상위 도메인에 반영될 때까지 시간이 걸릴 수 있다. 확인 방법:
```bash
dig +short A www.ohjumwhat.cloud
```
`1.201.114.178`이 나오면 된다. Caddy는 이 상태여야 인증서를 발급받는다.

### 2. 서버 방화벽

가비아 콘솔의 방화벽(보안 그룹)에서 아래 포트를 모든 IP(0.0.0.0/0)에 연다. 서버 안의 ufw는 아래 3단계에서 설정한다.

| 포트 | 용도 |
|---|---|
| 22/TCP | SSH. GitHub Actions 배포도 SSH로 들어온다. 러너 IP가 매번 바뀌므로 특정 IP로 제한하지 않고, 대신 키 로그인만 허용한다. |
| 80/TCP (HTTP) | **꼭 열어야 한다.** Let's Encrypt 인증서 발급·갱신(HTTP-01 인증)에 쓰이고, http:// 로 들어온 요청을 https로 리디렉션한다. 실제 서비스는 HTTPS로만 한다. |
| 443/TCP (HTTPS) | 서비스 |
| 443/UDP | HTTP/3(선택). 막아도 HTTP/2로 동작한다. |

DB(5432)와 앱(8080)은 컨테이너 안에서만 쓰므로 열지 않는다. 앱의 관리 포트(8081, 헬스·지표)는 서버의 127.0.0.1에만 열리므로 방화벽에서도 열지 않는다(아래 「모니터링」).

### 3. 서버 초기 설정 (SSH로 접속해서 한 번 실행)

```bash
ssh -i ohjumwhat.pem <SSH 사용자>@1.201.114.178
```

Docker Engine과 Compose 플러그인을 설치한다(Ubuntu, Docker 공식 저장소).
```bash
sudo apt-get update && sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update && sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"   # root가 아니면 실행 후 다시 로그인한다
```

방화벽과 SSH를 설정한다. SSH 키 로그인이 되는 것을 먼저 확인한 뒤 비밀번호 로그인을 끈다.
```bash
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp
sudo ufw --force enable
sudo sed -i 's/^#\?PasswordAuthentication .*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo grep -rl 'PasswordAuthentication yes' /etc/ssh/sshd_config.d/ 2>/dev/null | xargs -r sudo sed -i 's/PasswordAuthentication yes/PasswordAuthentication no/'
sudo systemctl restart ssh
sudo sshd -T | grep -i passwordauthentication   # "passwordauthentication no"가 나와야 한다
```

배포 폴더와 `.env`를 만든다. 값은 `deploy/.env.example`을 참고한다.
```bash
mkdir -p ~/ohjumwhat && cd ~/ohjumwhat
nano .env        # 아래 예시를 붙여 넣고 값을 채운다
chmod 600 .env
```
```bash
DB_NAME=ohjumwhat
DB_USERNAME=ohjumwhat
DB_PASSWORD=<openssl rand -hex 24 로 만든 값>
GOOGLE_CLIENT_ID=<구글 OAuth 클라이언트 ID>
GOOGLE_CLIENT_SECRET=<구글 OAuth 클라이언트 시크릿>
ADMIN_EMAILS=<관리자 콘솔을 쓸 구글 계정 이메일, 쉼표로 여러 개>
KAKAO_REST_KEY=<카카오 로컬 REST API 키, 비우면 지도·거리 끔>
NAVER_MAP_KEY_ID=<네이버 지도 NCP Maps Client ID, 비우면 지도 끔>
APP_DOMAIN=www.ohjumwhat.cloud
APP_APEX_DOMAIN=ohjumwhat.cloud
APP_IMAGE=ghcr.io/matuna01/ohjumwhat:latest
```

`.env`는 docker compose(`env_file`)가 읽는 형식이다. 셸에서 `source .env`로 읽지 않는다(셸 문법이 아닌 값이 있으면 그 줄을 명령으로 실행한다).
- 목록 값(`ADMIN_EMAILS`)은 쉼표로만 구분하고 띄어 쓰지 않는다: `ADMIN_EMAILS=a@gmail.com,b@gmail.com`
- 공백이 들어간 값은 따옴표로 감싼다(`KEY="a b"`). `$`가 들어간 값은 작은따옴표로 감싼다(`KEY='a$b'`). 따옴표가 없거나 큰따옴표면 compose가 `$`를 변수로 바꾼다.

일일 백업을 등록한다(매일 04:00, DB와 프로필 사진, 14일 보관).
```bash
( crontab -l 2>/dev/null; echo "0 4 * * * $HOME/ohjumwhat/backup.sh >> $HOME/ohjumwhat/backup.log 2>&1" ) | crontab -
```

### 4. GitHub Secrets (저장소 → Settings → Secrets and variables → Actions)

| 이름 | 값 |
|---|---|
| `DEPLOY_HOST` | `1.201.114.178` |
| `DEPLOY_USER` | 서버 SSH 사용자 (예: `ubuntu` 또는 `root`) |
| `DEPLOY_SSH_KEY` | `ohjumwhat.pem` 파일 내용 전체 |

터미널에서 설정하려면 다음과 같이 한다.
```bash
gh secret set DEPLOY_HOST -R MaTuna01/OhJumWhat --body "1.201.114.178"
gh secret set DEPLOY_USER -R MaTuna01/OhJumWhat --body "<SSH 사용자>"
gh secret set DEPLOY_SSH_KEY -R MaTuna01/OhJumWhat < ohjumwhat.pem
```

### 5. Google Cloud Console (API 및 서비스 → 사용자 인증 정보 → 오점왓 웹 클라이언트)

- 승인된 자바스크립트 원본: `https://www.ohjumwhat.cloud`
- 승인된 리디렉션 URI: `https://www.ohjumwhat.cloud/login/oauth2/code/google`
- **OAuth 동의 화면의 게시 상태를 "프로덕션"으로 바꾼다.** "테스트" 상태에서는 등록한 테스트 사용자만 로그인할 수 있다. openid·profile·email 범위만 쓰므로 구글 검수는 필요 없다.

### 5-1. 지도 키 (카카오 로컬 · 네이버 지도)

조직·식당 주소를 좌표로 바꾸는 일은 카카오 로컬 REST(서버), 화면의 지도는 네이버 지도(NCP Maps, 브라우저)가 맡는다. 키가 없으면 지도·거리만 꺼지고 나머지는 그대로 동작한다.

- **카카오 디벨로퍼스**(https://developers.kakao.com): 애플리케이션 → 앱 설정에서 **「카카오맵」을 켠다**(안 켜면 로컬 API가 403). 「앱 키 → REST API 키」를 서버 `.env`의 `KAKAO_REST_KEY`에 넣는다. 무료량(키워드 검색 하루 10만 건) 안에서는 결제 수단이 필요 없다.
- **NCP 콘솔**(https://console.ncloud.com): Maps → Application 등록에서 **Dynamic Map**을 고르고, Web 서비스 URL에 `https://www.ohjumwhat.cloud`, `http://localhost:5173`, `https://localhost`(로컬 Docker 확인용)를 넣는다. Client ID를 `NAVER_MAP_KEY_ID`에 넣는다(브라우저에 보이는 공개 값이라 등록한 도메인에서만 동작한다). 무료량을 넘지 않게 콘솔에서 사용 한도를 걸어 둔다.
- 약관상 좌표·검색 결과는 저장하지 않고 볼 때마다 받는다(저장하는 것은 사용자가 입력한 주소뿐).
- 운영 CSP(`deploy/Caddyfile`)에 네이버 지도 출처(https: `*.pstatic.net`, http: `*.map.naver.net`·`static.naver.net`, 공통: `oapi.map.naver.com`·`kr-col-ext.nelo.navercorp.com`)와 `style-src-attr 'unsafe-inline'`(지도 스크립트의 style 속성)이 들어 있다. 지도 스크립트가 페이지 스킴에 따라 출처를 바꾸므로 CSP를 고치면 https로 확인한다.

### 5-2. Firebase 푸시 (웹 푸시 알림, FCM)

새 방명록·글 제한 경고·새 쪽지를 웹 푸시로 알린다(이슈 #102). 값이 하나라도 없거나 잘못되면 푸시만 꺼지고(마이페이지 「알림」 카드가 숨겨진다) 서버는 그대로 뜬다. 값 6개는 서버 `.env`와 Notion 「개발 필요 파일」에 둔다.

| `.env` 이름 | 어디서 | 공개 여부 |
|---|---|---|
| `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_APP_ID`, `FIREBASE_MESSAGING_SENDER_ID` | Firebase 프로젝트 설정 → 일반 → 내 앱(웹 앱)의 `firebaseConfig` | 공개(브라우저가 `GET /api/config`로 받는다) |
| `FIREBASE_VAPID_KEY` | 프로젝트 설정 → 클라우드 메시징 → 웹 구성 → 웹 푸시 인증서 → 키 쌍 생성 | 공개 |
| `FIREBASE_SERVICE_ACCOUNT_BASE64` | 아래 전용 서비스 계정의 JSON 키를 base64 한 줄로 | **비밀**(서버만) |

처음 만들 때(한 번)
1. Firebase 콘솔(https://console.firebase.google.com)에서 **새 프로젝트**를 만든다. 구글 로그인(OAuth)용 GCP 프로젝트와 따로 두고, Google 애널리틱스는 끈다.
2. 웹 앱(`</>`)을 등록한다. Firebase 호스팅은 설정하지 않는다. `firebaseConfig`의 apiKey·projectId·messagingSenderId·appId만 쓴다.
3. 클라우드 메시징 탭에서 「Firebase Cloud Messaging API(V1)」가 사용 설정인지 보고, 웹 푸시 인증서의 키 쌍을 만든다(VAPID 공개키).
4. Google Cloud 콘솔(같은 프로젝트)의 「API 및 서비스 → 라이브러리」에서 **FCM Registration API**, **Firebase Installations API**, **Firebase Cloud Messaging API**를 켠다(FCM Registration API가 꺼져 있으면 브라우저 등록이 실패한다).
5. 「사용자 인증 정보」의 **Browser key (auto created by Firebase)**를 제한한다: 웹사이트 `https://www.ohjumwhat.cloud/*`, `http://localhost:5173/*`, `https://localhost/*`, API는 Firebase Installations API·FCM Registration API만.
6. 「IAM 및 관리자 → 서비스 계정」에서 전용 계정(예: `ohjumwhat-push`)을 만들고 역할은 **Firebase Cloud Messaging API 관리자** 하나만 준다. 키 → 새 키 만들기 → JSON. (Firebase 「서비스 계정」 탭의 기본 Admin SDK 계정은 권한이 넓어서 쓰지 않는다.)
7. JSON을 base64 한 줄로 바꿔 `.env`에 넣는다. 파일 마운트 대신 환경변수를 쓰는 것은 지금의 `env_file` 방식·「비우면 끈다」 규칙을 그대로 쓰기 위해서다.
   ```bash
   base64 -i 받은-키-파일.json | tr -d '\n' | pbcopy
   ```
   받은 JSON 파일은 지운다(저장소 폴더에 두지 않는다, `.gitignore`가 `*service-account*.json`·`*firebase-adminsdk*.json`을 막는다).

키 교체·끄기
- 서비스 계정 키 교체: 같은 서비스 계정에 새 JSON 키를 만들어 `.env`를 바꾸고 `docker compose up -d`(앱 재시작) → 콘솔에서 옛 키를 삭제한다.
- VAPID 키를 다시 만들면 이미 켠 기기의 구독이 옛 키라 보내기가 실패한다. 화면이 다음에 열릴 때 구독의 키가 다른 것을 보고 구독을 새로 만들어 다시 등록하지만(`lib/pushClient.ts`), 그 사이 알림은 못 받는다. 그래도 안 오면 마이페이지에서 알림을 껐다 켜면 된다(끌 때 브라우저 구독도 지운다).
- 푸시를 끄려면 `.env`에서 `FIREBASE_SERVICE_ACCOUNT_BASE64`를 비우고 앱을 재시작한다(화면 안 배지 알림은 그대로).
- 운영 CSP에 Firebase 출처(`connect-src`의 `https://firebaseinstallations.googleapis.com`·`https://fcmregistrations.googleapis.com`, `worker-src 'self'`, `manifest-src 'self'`)가 들어 있다. 고치면 https로 확인한다.
- 아이폰·아이패드는 iOS 16.4 이상에서 **홈 화면에 추가한 오점왓**으로만 푸시를 받는다(홈 화면 앱은 Safari와 로그인이 따로라 다시 로그인한다).

### 6. 첫 배포

`dev`를 `main`에 머지하면 Deploy 워크플로가 실행된다. 머지 이후에는 Actions 탭에서 수동으로 다시 실행(workflow_dispatch)할 수도 있다.
```bash
gh pr create -R MaTuna01/OhJumWhat --base main --head dev --title "릴리스"
```
확인 방법:
```bash
curl -I https://www.ohjumwhat.cloud               # 200, HTTP/2
curl -I http://ohjumwhat.cloud                    # https://www.ohjumwhat.cloud 로 리디렉션
```

## 릴리스와 버전

`dev` → `main` 승격(사용자 결정) 한 번이 릴리스 하나다. 버전은 `vMAJOR.MINOR.PATCH`로 붙인다.

0. DB 스키마가 바뀌는 릴리스(새 `V{n}__*.sql`)라면 승격 전에 서버에서 `./backup.sh`로 백업한다. 새 설정 키가 있으면 서버 `.env`에 먼저 넣는다.
1. 릴리스 이슈(`[release] vX.Y.Z`)를 만들고 `release/vX.Y.Z` 브랜치에서 버전을 올린다(이슈·브랜치·커밋 규칙은 `CLAUDE.md` 「작업 규칙」): `ohjumwhat-backend/build.gradle.kts`의 `version`, 프론트는 `npm version X.Y.Z --no-git-tag-version`(package.json·package-lock.json). `CHANGELOG.md`에 바뀐 점을 적고 `dev`에 머지한다.
   - 사용자에게 보이는 변경이 있으면 업데이트 글 `ohjumwhat-backend/src/main/resources/release-notes/X.Y.Z.md`도 함께 적는다. 배포 뒤 서버가 뜰 때 새 소식에 자동으로 올라간다(게시 시각 = 배포 시각).
   - 형식: 첫 줄 `# 제목`, 나머지는 본문(빈 줄 = 문단, `- ` = 목록). 사용자 말투로 3~5줄, DB·마이그레이션·CSP 같은 개발 용어는 쓰지 않는다. CHANGELOG는 개발자용이라 따로 쓴다.
   - 사용자에게 보이는 변경이 없으면(인프라 수정 등) 파일을 만들지 않는다. 배포 뒤에 고쳐도 다시 알리지 않는다.
2. `dev` → `main` 릴리스 PR(제목 `[#이슈 번호] - 릴리스 vX.Y.Z: 요약`, 본문에 포함 이슈·PR)을 만들어 머지한다. Deploy 워크플로가 배포한다.
   - 운영 버그를 고치는 릴리스(보통 PATCH)는 제목 앞에 `[hotfix]`를 붙이고(`[hotfix] [#이슈 번호] - 릴리스 vX.Y.Z: …`, 라벨 `hotfix`·`bug`), 고친 문제를 사용자 말투로 알리는 업데이트 글을 함께 낸다(예: `release-notes/1.7.1.md`).
3. 배포가 끝나면 `main`의 머지 커밋에 태그와 GitHub Release를 만든다.
```bash
git tag -a vX.Y.Z -m "vX.Y.Z" origin/main && git push origin vX.Y.Z
gh release create vX.Y.Z -R MaTuna01/OhJumWhat --title "vX.Y.Z" --notes "<CHANGELOG의 해당 절>"
```

## 관리자 지정

관리자 콘솔(`/admin`)은 서버 `.env`의 `ADMIN_EMAILS`에 적힌 구글 계정만 쓸 수 있다. 바꾼 뒤 앱을 다시 띄우면 목록과 맞춰진다(목록에 없는 관리자는 해제된다).
```bash
cd ~/ohjumwhat
nano .env                       # ADMIN_EMAILS=a@gmail.com,b@gmail.com (쉼표 뒤에 띄어 쓰지 않는다)
docker compose up -d app        # 설정을 다시 읽도록 앱 컨테이너를 다시 만든다
docker compose logs app | grep 관리자
```

## 운영

```bash
cd ~/ohjumwhat
docker compose ps                    # 상태
docker compose logs -f app           # 앱 로그 (caddy, db도 같은 방식)
docker compose restart app           # 앱만 재시작
./backup.sh                          # 수동 백업 → backups/
```

**투표 채팅 연결**: 채팅 받기(WebSocket)는 앱 메모리에 연결을 들고 있어서 배포·재시작 때 모두 끊긴다. 화면이 1초부터 두 배씩(최대 30초) 기다렸다 스스로 다시 연결하고 놓친 메시지를 다시 받으므로 따로 할 일은 없다. 앱을 두 대 이상 띄우면 메시지가 같은 앱에 연결된 사람에게만 가므로, 그때는 외부 브로커(Redis 등)가 필요하다.

**롤백**: `.env`의 `APP_IMAGE`를 이전 커밋 태그로 바꾸고 다시 띄운다.
```bash
sed -i 's|^APP_IMAGE=.*|APP_IMAGE=ghcr.io/matuna01/ohjumwhat:<이전 커밋 sha>|' .env
docker compose pull app && docker compose up -d
```
다음 배포 전에 `APP_IMAGE`를 `:latest`로 되돌린다(옛 이미지로 남아 있으면 배포 헬스 체크가 실패한다). DB 스키마(Flyway)는 앞으로만 적용되므로, 스키마가 바뀐 버전을 되돌릴 때는 호환 여부를 먼저 확인한다. v1.11.1까지의 이미지에는 `/healthz`와 관리 포트가 없어서 업타임 체크가 장애로 알리고 Netdata의 앱 지표가 끊긴다(아래 「모니터링」).

**백업에서 복원**: DB(`users.photo_key`)가 사진 파일을 가리키므로 같은 시각의 DB와 사진을 함께 복원한다. 채팅 사진(`photos/chat`)은 백업하지 않는다(아래 「채팅 사진」).
```bash
gunzip -c backups/ohjumwhat-YYYYMMDD-HHMM.sql.gz | docker compose exec -T db psql -U ohjumwhat -d ohjumwhat
# 사진은 앱 컨테이너(app 사용자)로 풀어야 파일 소유자가 맞는다.
docker compose exec -T app tar xzf - -C /data < backups/ohjumwhat-photos-YYYYMMDD-HHMM.tar.gz
```

**프로필 사진**: 올린 사진은 `photos` 볼륨(앱 컨테이너 `/data/photos`)에 `{키}.jpg`로 있다. 한 장에 수 KB~수십 KB다. `docker compose down -v`는 볼륨까지 지우므로 사진(과 DB)이 사라진다. 볼륨을 남기려면 `down`만 쓴다.

**채팅 사진**: 채팅에서 보낸 사진은 같은 볼륨의 `/data/photos/chat`(`CHAT_PHOTOS_DIR`)에 `{키}.jpg`(원본, 긴 변 1600px, 수백 KB)와 `{키}_t.jpg`(썸네일)로 있다. 30일 동안만 보여주고, 매일 04:00(KST)에 앱이 보관 기간이 지났거나 지운·없어진 메시지의 파일을 지운다. 30일이면 지워지는 사진이라 `backup.sh`는 이 폴더를 백업하지 않는다(복원하면 채팅 사진은 「사진을 불러올 수 없어요」로 보인다). 디스크 사용량은 Netdata의 Disk Space로 본다.

## 모니터링

서버 한 대·소규모라 Prometheus·Grafana를 따로 띄우지 않고 아래 세 가지로 본다(이슈 #107, Notion 「27. 서버 모니터링 구축」).

| 무엇을 | 어떻게 | 어디서 보나 |
|---|---|---|
| 서비스가 살아 있는지 | 외부 업타임 체크가 `https://www.ohjumwhat.cloud/healthz`를 5분마다 부른다 | 업타임 서비스의 알림(이메일·앱) |
| 서버·컨테이너 사용량(CPU·메모리·스왑·디스크·네트워크) | 서버에 직접 설치한 Netdata | SSH 터널로 Netdata 대시보드 |
| 앱 트래픽(엔드포인트별 요청 수·응답 시간, JVM 힙, DB 커넥션 풀, 채팅 연결 수) | Netdata가 앱의 관리 포트 `127.0.0.1:8081/actuator/prometheus`를 읽는다 | 같은 대시보드 |

- `/healthz`는 앱 포트의 공개 헬스 체크로, 상태(`{"status":"UP"}`)만 준다. DB·디스크·준비 상태를 보므로 DB가 멈추면 503 `DOWN`이 되고(DB 연결을 기다리느라 약 30초 걸린다), 앱이 뜨는 중이거나 멈추는 중에도 503이다. 배포 헬스 체크도 이 주소를 쓴다.
- 관리 포트(8081)는 인증 없이 열리므로 네트워크로만 막는다. 서버의 127.0.0.1에만 열려 있고 Caddy가 프록시하지 않으며, 가비아 방화벽에서도 열지 않는다. 앱 포트의 `/actuator/**`는 없는 경로라 화면(index.html)이 나온다.
- JVM은 `MaxRAMPercentage=50`이라 서버 RAM의 절반(약 2GB)까지 힙을 잡고 잘 돌려주지 않는다. 서버 메모리 그래프보다 실제로 쓰는 힙(`jvm_memory_used_bytes{area="heap"}`)과 스왑 사용량으로 여유를 판단한다.
- 컨테이너 로그는 서비스마다 10MB × 5개까지만 남는다(`docker compose logs`로 볼 수 있는 범위도 그만큼이다). 로그 설정이 들어간 첫 배포는 db·caddy 컨테이너도 다시 만들어 수십 초 끊기고, 다시 만든 컨테이너의 지난 로그는 사라진다. 남겨야 하면 배포 전에 `docker compose logs --no-color > logs-before-monitoring.txt`로 저장한다.

### 1. 설치 전 기준선 측정

지금 사용량을 먼저 적어 둔다. 사용 가능한 메모리(`available`)가 1GB보다 적으면 Netdata 대신 더 가벼운 Beszel을 검토한다.
```bash
free -h; swapon --show; docker stats --no-stream; df -h /; sudo du -sh /var/lib/docker/containers
```
Docker 엔진이 28 이상인지도 본다. 그보다 낮으면 127.0.0.1에만 연 포트(관리 포트)에 같은 네트워크 구간의 다른 서버가 닿을 수 있어(28.0에서 막혔다) Docker를 먼저 올린다.
```bash
docker version --format '{{.Server.Version}}'
```

### 2. 외부 업타임 체크 (UptimeRobot)

Netdata는 서버와 함께 꺼지므로 서버 밖에서 따로 확인한다. 가입할 때 무료 플랜의 이용 조건(상업적 이용)을 확인한다.

1. https://uptimerobot.com 에 가입한다(무료 플랜: 모니터 50개, 5분 간격).
2. New monitor → **Keyword**, URL `https://www.ohjumwhat.cloud/healthz`, 키워드 `"status":"UP"`, 「키워드가 없으면 장애(Alert when keyword not exists)」, 간격 5분. DB가 멈추면 응답이 30초 가까이 걸리므로 시간 초과를 넉넉히(30초 이상) 둔다.
   - 상태 코드만 보는 HTTP(s) 모니터는 쓰지 않는다. `/healthz`가 없는 이미지(롤백한 옛 버전 등)에서는 SPA가 화면(index.html)을 200으로 주므로, DB가 죽어도 정상으로 보인다.
3. 알림 받을 곳(이메일, 모바일 앱)을 정한다.

장애 알림이 오면 서버에서 `docker compose ps`와 `docker compose logs --tail 200 app`부터 본다.

### 3. Netdata 설치 (서버에 직접, 한 번)

공식 설치 스크립트로 안정판을 깔고 익명 통계는 끈다. 스크립트의 기본 채널은 nightly라서 `--stable-channel`을 꼭 붙인다. Netdata Cloud에는 연결하지 않는다(나중에 원하면 아래 「Netdata Cloud」). 자동 업데이트(매일)는 그대로 둔다.
```bash
wget -O /tmp/netdata-kickstart.sh https://get.netdata.cloud/kickstart.sh && sh /tmp/netdata-kickstart.sh --stable-channel --disable-telemetry --non-interactive
```
설치 직후에는 대시보드가 모든 주소(`*:19999`)에서 뜨지만, ufw와 가비아 방화벽이 22·80·443만 열어 두었으므로 밖에서는 닿지 않는다. 바로 아래 설정으로 127.0.0.1에만 열리게 바꾼다.

설정 파일(`/etc/netdata/netdata.conf`)을 연다. 처음에는 주석만 있는 것이 정상이다.
```bash
cd /etc/netdata && sudo ./edit-config netdata.conf
```
`edit-config`는 우분투에서 nano로 열린다(`sudo`가 `EDITOR`를 넘기지 않고, 그다음으로 쓰는 `editor` 명령이 보통 nano를 가리킨다). vi로 열려면 `--editor vi`를 붙인다. 아래의 다른 설정 파일도 같다.
```bash
cd /etc/netdata && sudo ./edit-config --editor vi netdata.conf
```
아래를 넣는다. 대시보드는 서버 안(127.0.0.1)에서만 열고, 메모리·CPU를 쓰는 이상 탐지(ML)는 끈다. 보관 기간은 기본값(초 단위 14일 · 분 단위 3개월 · 시간 단위 2년, 단계마다 1GiB, 메타데이터까지 4GB쯤)으로 충분하다. 점심시간 패턴은 분 단위 기록으로 몇 주를 비교한다.
```ini
[web]
    bind to = 127.0.0.1

[ml]
    enabled = no
```

컨테이너 이름(app·db·caddy)으로 보려면 netdata 사용자가 Docker에 물어볼 수 있어야 한다. 안 하면 컨테이너 ID 앞 12자로 보이고, 배포 때마다 ID가 바뀌어 알아보기 어렵다. docker 그룹은 root와 같은 권한이라 대시보드를 밖에 열지 않는 지금 구성에서만 쓴다.
```bash
sudo usermod -aG docker netdata
```
그러면 Netdata가 Docker 컨테이너를 찾아 수집 작업을 자동으로 만드는데, Postgres 컨테이너에는 없는 계정으로 접속하려다 실패해 로그만 쌓인다. 자동 찾기를 끈다(컨테이너별 CPU·메모리는 이것과 상관없이 보인다).
```bash
cd /etc/netdata && sudo ./edit-config go.d/sd/docker.conf    # disabled: no → disabled: yes
```
첫 줄의 `disabled: no`를 `disabled: yes`로 바꾼다. vi로 열었으면 `:%s/^disabled: no/disabled: yes/ | wq`를 입력하면 바꾸고 저장한 뒤 나간다(「Pattern not found」가 나오면 저장하지 않으니 직접 고친다). 바뀌었는지 확인한다.
```bash
grep -n '^disabled' /etc/netdata/go.d/sd/docker.conf    # 1:disabled: yes
```

### 4. 앱 지표 수집 (Spring → Netdata)

앱 지표는 자동으로 찾지 못하므로 수집 작업을 직접 적는다. 먼저 지표가 나오는지와 개수(시계열 수)를 본다. 로컬에서는 약 190개였지만, 요청 지표는 호출된 경로·상태 조합마다 늘어난다.
```bash
curl -s http://127.0.0.1:8081/actuator/prometheus | grep -vc '^#'
```
Netdata는 시계열이 전체 한도(기본 2000)를 넘으면 그 수집 전체를, 지표 하나의 한도(기본 200)를 넘으면 그 지표를 말없이 버린다. 그래서 한도를 넉넉히 올려 둔다. 1~2주 뒤 위 명령으로 다시 세어 한도에 가까우면 늘린다.
```bash
cd /etc/netdata && sudo ./edit-config go.d/prometheus.conf
```
```yaml
jobs:
  - name: ohjumwhat
    url: http://127.0.0.1:8081/actuator/prometheus
    update_every: 10
    max_time_series: 3000
    max_time_series_per_metric: 1000
```
설정을 다 넣었으면 다시 띄우고 확인한다.
```bash
sudo systemctl restart netdata
ss -ltnp | grep 19999                    # 127.0.0.1:19999만 나와야 한다
sudo -u netdata -s                       # netdata 사용자로 수집 작업을 한 번 돌려 본다(끝나면 exit)
cd /usr/libexec/netdata/plugins.d/ && ./go.d.plugin -d -m prometheus -j ohjumwhat
```

### 5. 대시보드 보기

내 PC에서 SSH 터널을 연 채로 브라우저에서 http://localhost:19999 를 연다.
```bash
ssh -i ohjumwhat.pem -L 19999:localhost:19999 <SSH 사용자>@1.201.114.178
```

| 알고 싶은 것 | 볼 차트 |
|---|---|
| 서버가 얼마나 한가한지 | System Overview의 CPU, RAM, Swap(사용량·swap in/out), Disk Space |
| 컨테이너별 사용량 | Containers & VMs의 app·db·caddy CPU·메모리 |
| 점심시간 요청 수 | `http_server_requests_seconds_count`(초당 요청 수, uri·status별). 대시보드에서 `ohjumwhat`이나 `http_server_requests`로 찾는다 |
| 응답 시간 | `http_server_requests_seconds_sum` ÷ `_count`(평균), `http_server_requests_seconds_max` |
| JVM 메모리 여유 | `jvm_memory_used_bytes`(area=heap)와 `jvm_memory_max_bytes` |
| DB 커넥션 | `hikaricp_connections_active`·`hikaricp_connections_pending`(기다리는 요청이 생기면 부족) |
| 채팅을 보고 있는 화면 수 | `ohjumwhat_chat_connections` |

Netdata는 RAM 100~350MB, CPU 한 코어의 1~5% 정도를 쓴다(공식 안내, ML을 끄면 더 적다). 설치 뒤 `systemctl status netdata`로 실제 사용량을 확인하고, 1~2주 동안 점심시간 피크를 본 다음 서버 사양(유지·축소)을 정한다.

**Netdata Cloud(선택)**: 터널 없이 브라우저로 보고 싶으면 무료 플랜(노드 5대까지)에 연결할 수 있다. 무료 플랜의 알림은 이메일·Discord 같은 기본 방식이다(모바일 앱 푸시는 유료). 연결은 대시보드의 「Connect」나 `/etc/netdata/claim.conf`로 한다(https://github.com/netdata/netdata/blob/master/src/claim/README.md). Cloud에 연결하면 대시보드를 밖에 열지 않는다는 전제가 바뀌므로, netdata 사용자를 docker 그룹(root와 같은 권한)에 둘지 다시 정한다. 서비스가 죽었는지는 Cloud가 아니어도 위 업타임 체크가 알려준다.
