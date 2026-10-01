import { queryOptions, useQuery } from '@tanstack/react-query'
import { type DeployedBuild, isOutdated, parseBuild } from '../lib/build.ts'

const CHECK_INTERVAL_MS = 5 * 60_000

async function fetchDeployedBuild(): Promise<DeployedBuild> {
  const response = await fetch('/version.json', { cache: 'no-store' })
  if (!response.ok) throw new Error(`version.json ${response.status}`)
  const build = parseBuild(await response.json())
  // 배포 중(컨테이너 교체)이라 이상한 응답이 오면 실패로 두어 마지막으로 읽은 값을 그대로 쓴다.
  if (!build) throw new Error('version.json 형식이 달라요')
  return build
}

/** 서버에 배포된 빌드. 개발 서버에는 /version.json이 없어서 운영 빌드에서만 읽는다. */
const deployedBuild = queryOptions({
  queryKey: ['build'] as const,
  queryFn: fetchDeployedBuild,
  enabled: import.meta.env.PROD,
})

/**
 * 지금 서비스의 버전(「새 소식」의 현재 버전). 화면 코드에 넣은 값이 아니라 서버에 배포된 버전이라,
 * 배포 전부터 열려 있던 탭도 새 버전을 보여준다. 아직 못 읽었거나 개발 서버면 이 화면 코드의 버전(package.json)
 */
export function useDeployedVersion(): string {
  const { data } = useQuery(deployedBuild)
  return data?.version ?? __APP_VERSION__
}

/**
 * 배포 전부터 열려 있던 탭이면 true(새 버전 안내).
 * 창이 다시 보일 때와 5분마다 /version.json을 읽어 지금 화면 코드의 빌드 ID와 비교하고, 한 번 다르면 더 확인하지 않는다.
 */
export function useNewBuild(): boolean {
  const { data } = useQuery({
    ...deployedBuild,
    refetchInterval: (query) => (isOutdated(__BUILD_ID__, query.state.data) ? false : CHECK_INTERVAL_MS),
    refetchOnWindowFocus: (query) => !isOutdated(__BUILD_ID__, query.state.data),
  })
  return isOutdated(__BUILD_ID__, data)
}
