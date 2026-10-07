import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import './index.css'
import { isRestricted, isUnauthorized } from './lib/api.ts'
import { meQueryKey } from './queries/me.ts'
import { sanctionKeys } from './queries/sanctions.ts'
import { router } from './router.tsx'

// 쓰는 도중 세션이 만료되면(401) 내 정보를 다시 불러와 RequireAuth가 로그인 화면으로 보내게 한다.
// 관리자가 막 이용을 제한했으면(423) 내 정보(제재)와 안내를 다시 받아 막힌 자리를 바로 바꾼다(안내 창은 다음 화면에서 뜬다).
const onError = (error: unknown, queryKey?: readonly unknown[]) => {
  if (isUnauthorized(error) && queryKey?.[0] !== meQueryKey[0]) {
    queryClient.invalidateQueries({ queryKey: meQueryKey })
  }
  if (isRestricted(error)) {
    queryClient.invalidateQueries({ queryKey: meQueryKey })
    queryClient.invalidateQueries({ queryKey: sanctionKeys.alerts })
  }
}

const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError: (error, query) => onError(error, query.queryKey) }),
  mutationCache: new MutationCache({ onError: (error) => onError(error) }),
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: true },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
