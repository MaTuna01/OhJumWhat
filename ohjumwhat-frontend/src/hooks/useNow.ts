import { useEffect, useState } from 'react'

/** 남은 시간 표시처럼 시간이 흐르면 다시 그려야 하는 곳에서 쓴다. */
export function useNow(intervalMs = 15_000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(timer)
  }, [intervalMs])
  return now
}
