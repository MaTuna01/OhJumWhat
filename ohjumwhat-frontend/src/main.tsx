import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import './index.css'
import { isUnauthorized } from './lib/api.ts'
import { meQueryKey } from './queries/me.ts'
import { router } from './router.tsx'

// 쓰는 도중 세션이 만료되면(401) 내 정보를 다시 불러와 RequireAuth가 로그인 화면으로 보내게 한다.
const onUnauthorized = (error: unknown, queryKey?: readonly unknown[]) => {
  if (isUnauthorized(error) && queryKey?.[0] !== meQueryKey[0]) {
    queryClient.invalidateQueries({ queryKey: meQueryKey })
  }
}

const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError: (error, query) => onUnauthorized(error, query.queryKey) }),
  mutationCache: new MutationCache({ onError: (error) => onUnauthorized(error) }),
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
