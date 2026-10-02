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
| `Dockerfile` | 프론트 빌드 → `static/`에 복사 → Spring Boot jar → JRE 21 이미지(비루트 사용자, `MaxRAMPercentage=50`) |
| `deploy/docker-compose.yml` | 운영 스택. DB는 외부 포트를 열지 않고, 80·443은 Caddy만 연다. |
| `deploy/Caddyfile` | 인증서 자동 발급·갱신, 압축, 보안 헤더(CSP 등), 루트 도메인 → www 리디렉션. 바뀌면 배포 때 검증한 뒤 Caddy 컨테이너를 다시 만든다(파일 하나를 마운트해서 `up -d`만으로는 반영되지 않는다). |
| `deploy/backup.sh` | `pg_dump` 일일 백업(14일 보관) |
| `deploy/.env.example` | 서버 `.env` 템플릿 |
| `.github/workflows/ci.yml` | PR과 `dev` push에서 백엔드 테스트, 프론트 린트·테스트·빌드 |
| `.github/workflows/deploy.yml` | `main` push(또는 수동 실행) 시 CI → 이미지 → 배포 → 헬스 체크(2분) |

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

DB(5432)와 앱(8080)은 컨테이너 안에서만 쓰므로 열지 않는다.

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

일일 백업을 등록한다(매일 04:00, 14일 보관).
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
- 운영 CSP(`deploy/Caddyfile`)에 네이버 지도 출처(`oapi.map.naver.com`, `*.map.naver.net`, `static.naver.net`, `kr-col-ext.nelo.navercorp.com`)와 `style-src-attr 'unsafe-inline'`(지도 스크립트의 style 속성)이 들어 있다.

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
1. 기능 브랜치에서 버전을 올린다: `ohjumwhat-backend/build.gradle.kts`의 `version`, 프론트는 `npm version X.Y.Z --no-git-tag-version`(package.json·package-lock.json). `CHANGELOG.md`에 바뀐 점을 적고 `dev`에 머지한다.
   - 사용자에게 보이는 변경이 있으면 업데이트 글 `ohjumwhat-backend/src/main/resources/release-notes/X.Y.Z.md`도 함께 적는다. 배포 뒤 서버가 뜰 때 새 소식에 자동으로 올라간다(게시 시각 = 배포 시각).
   - 형식: 첫 줄 `# 제목`, 나머지는 본문(빈 줄 = 문단, `- ` = 목록). 사용자 말투로 3~5줄, DB·마이그레이션·CSP 같은 개발 용어는 쓰지 않는다. CHANGELOG는 개발자용이라 따로 쓴다.
   - 사용자에게 보이는 변경이 없으면(인프라 수정 등) 파일을 만들지 않는다. 배포 뒤에 고쳐도 다시 알리지 않는다.
2. `dev` → `main` 릴리스 PR을 만들어 머지한다. Deploy 워크플로가 배포한다.
3. 배포가 끝나면 `main`의 머지 커밋에 태그와 GitHub Release를 만든다.
```bash
git tag -a vX.Y.Z -m "vX.Y.Z" origin/main && git push origin vX.Y.Z
gh release create vX.Y.Z -R MaTuna01/OhJumWhat --title "vX.Y.Z" --notes "<CHANGELOG의 해당 절>"
```

## 관리자 지정

관리자 콘솔(`/admin`)은 서버 `.env`의 `ADMIN_EMAILS`에 적힌 구글 계정만 쓸 수 있다. 바꾼 뒤 앱을 다시 띄우면 목록과 맞춰진다(목록에 없는 관리자는 해제된다).
```bash
cd ~/ohjumwhat
nano .env                       # ADMIN_EMAILS=a@gmail.com,b@gmail.com
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

**롤백**: `.env`의 `APP_IMAGE`를 이전 커밋 태그로 바꾸고 다시 띄운다.
```bash
sed -i 's|^APP_IMAGE=.*|APP_IMAGE=ghcr.io/matuna01/ohjumwhat:<이전 커밋 sha>|' .env
docker compose pull app && docker compose up -d
```
다음 배포 전에 `APP_IMAGE`를 `:latest`로 되돌린다. DB 스키마(Flyway)는 앞으로만 적용되므로, 스키마가 바뀐 버전을 되돌릴 때는 호환 여부를 먼저 확인한다.

**백업에서 복원**
```bash
gunzip -c backups/ohjumwhat-YYYYMMDD-HHMM.sql.gz | docker compose exec -T db psql -U ohjumwhat -d ohjumwhat
```
