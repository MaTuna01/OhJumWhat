import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api.ts'

/** 화면이 실행 중에 받는 설정(GET /api/config). 프론트 빌드에는 키를 넣지 않는다. */
export type ClientConfig = {
  /** 네이버 지도 Client ID(공개 값, 등록한 도메인에서만 동작). 없으면 null */
  naverMapKeyId: string | null
  /** 카카오 로컬로 위치를 찾을 수 있는지(지도·거리·근처 식당 찾기) */
  placeSearch: boolean
}

export const configQueryKey = ['config'] as const

/** 서버를 다시 띄우기 전에는 바뀌지 않으므로 한 번만 부른다. */
export function useClientConfig() {
  return useQuery({
    queryKey: configQueryKey,
    queryFn: () => api<ClientConfig>('/api/config'),
    staleTime: Infinity,
    gcTime: Infinity,
  })
}

/** 지도를 그릴 수 있으면 네이버 지도 키, 아니면 null(지도 키와 위치 찾기가 모두 있어야 한다). */
export function useMapKey(): string | null {
  const { data } = useClientConfig()
  return data?.naverMapKeyId && data.placeSearch ? data.naverMapKeyId : null
}
