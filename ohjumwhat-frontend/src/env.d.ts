/** 빌드 ID. 새 버전 안내에서 /version.json과 비교한다(vite.config.ts의 define). */
declare const __BUILD_ID__: string

/** 이 화면 코드의 버전(package.json, vite.config.ts의 define). 「새 소식」의 현재 버전은 서버의 /version.json을 읽고, 못 읽으면 이 값을 쓴다. */
declare const __APP_VERSION__: string
