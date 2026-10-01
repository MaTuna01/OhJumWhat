/** 서버에 배포된 빌드(/version.json, vite.config.ts가 빌드마다 만든다) */
export type DeployedBuild = { buildId: string; version: string }

/** /version.json 응답을 읽는다. 모양이 이상한 응답(배포 중 오류 화면 등)은 null */
export function parseBuild(raw: unknown): DeployedBuild | null {
  if (typeof raw !== 'object' || raw === null) return null
  const { buildId, version } = raw as { buildId?: unknown; version?: unknown }
  if (typeof buildId !== 'string' || buildId === '' || typeof version !== 'string' || version === '') return null
  return { buildId, version }
}

/** 배포된 빌드가 지금 화면 코드와 다른 빌드인지(= 배포 전부터 열려 있던 탭인지). 못 읽었으면 그대로로 본다. */
export function isOutdated(currentBuildId: string, deployed: DeployedBuild | null | undefined): boolean {
  return deployed != null && deployed.buildId !== currentBuildId
}
