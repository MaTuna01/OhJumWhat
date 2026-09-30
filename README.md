# 오점왓 (ohjumwhat)

조직 안에서 점심·저녁 메뉴를 투표로 정하고, 메뉴별로 같이 먹을 사람을 모으는 웹 서비스.

- `ohjumwhat-backend/` Spring Boot 4 · Java 21 · Spring Security(구글 로그인) · JPA · Flyway
- `ohjumwhat-frontend/` React · TypeScript · Vite · React Router · TanStack Query · Tailwind CSS
- DB: PostgreSQL 18

구현 계획과 진행 현황은 [docs/PLAN.md](docs/PLAN.md)를 본다.

## 로컬 개발

### 1. 설정 파일 준비 (둘 다 git에 올리지 않는다)

```bash
cp .env.example .env
cp ohjumwhat-backend/src/main/resources/application.example.yml ohjumwhat-backend/src/main/resources/application.yml
```

`.env`에 DB 비밀번호와 구글 OAuth 클라이언트 값을 채운다. 실제 값은 Notion 「개발 필요 파일」 페이지에 있다.
구글 OAuth 클라이언트의 승인된 리디렉션 URI에는 `http://localhost:5173/login/oauth2/code/google`을 등록한다.

### 2. 실행

```bash
docker compose -f docker-compose.dev.yml up -d   # PostgreSQL
cd ohjumwhat-backend && ./gradlew bootRun                   # API 서버 :8080
cd ohjumwhat-frontend && npm install && npm run dev         # 프론트 :5173 (API는 8080으로 프록시)
```

브라우저에서 http://localhost:5173 을 연다.

### 테스트

```bash
cd ohjumwhat-backend && ./gradlew test    # Docker 필요 (Testcontainers)
cd ohjumwhat-frontend && npm test
```
