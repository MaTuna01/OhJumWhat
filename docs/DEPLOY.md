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
| `deploy/Caddyfile` | 인증서 자동 발급·갱신, 압축, 루트 도메인 → www 리디렉션 |
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
