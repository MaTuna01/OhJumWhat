import { useEffect, useState } from 'react'

/** 입력이 멈춘 뒤 delay(ms)가 지나면 값을 넘겨준다(검색어를 칠 때마다 요청하지 않도록). */
export function useDebouncedValue<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return debounced
}
