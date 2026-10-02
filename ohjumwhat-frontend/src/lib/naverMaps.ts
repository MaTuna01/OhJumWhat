declare global {
  interface Window {
    /** 네이버 지도 인증(키·도메인)이 실패하면 지도 스크립트가 부른다. */
    navermap_authFailure?: () => void
  }
}

const SCRIPT_URL = 'https://oapi.map.naver.com/openapi/v3/maps.js'

let loading: Promise<typeof naver.maps> | null = null

/**
 * 네이버 지도(NCP Maps JS v3) 스크립트를 한 번만 불러온다. 실패하면 다음에 다시 시도할 수 있게 비운다.
 * 스크립트가 뜬 뒤에도 내부 모듈이 다 올라와야(jsContentLoaded) 지도를 만들 수 있다.
 */
export function loadNaverMaps(keyId: string): Promise<typeof naver.maps> {
  if (loading) return loading
  loading = new Promise<typeof naver.maps>((resolve, reject) => {
    const fail = (message: string) => {
      loading = null
      reject(new Error(message))
    }
    window.navermap_authFailure = () => fail('네이버 지도 인증에 실패했어요.')
    const script = document.createElement('script')
    script.src = `${SCRIPT_URL}?ncpKeyId=${encodeURIComponent(keyId)}`
    script.async = true
    script.onerror = () => {
      script.remove()
      fail('네이버 지도를 불러오지 못했어요.')
    }
    script.onload = () => {
      if (typeof naver === 'undefined' || !naver.maps) {
        fail('네이버 지도를 불러오지 못했어요.')
        return
      }
      if (naver.maps.jsContentLoaded) resolve(naver.maps)
      else naver.maps.onJSContentLoaded = () => resolve(naver.maps)
    }
    document.head.appendChild(script)
  })
  return loading
}
