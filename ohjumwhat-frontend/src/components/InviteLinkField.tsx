import { useEffect, useState } from 'react'
import { inviteUrl } from '../lib/invite.ts'
import { inputClass } from '../lib/ui.ts'
import Button from './Button.tsx'

export default function InviteLinkField({ token }: { token: string }) {
  const url = inviteUrl(token)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!copied) return
    const timer = setTimeout(() => setCopied(false), 2000)
    return () => clearTimeout(timer)
  }, [copied])

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
    } catch {
      // 클립보드 권한이 없으면 사용자가 입력창에서 직접 복사한다.
    }
  }

  return (
    <div className="flex gap-2">
      <input
        readOnly
        value={url}
        aria-label="초대 링크"
        onFocus={(e) => e.currentTarget.select()}
        className={`${inputClass} min-w-0 font-mono text-xs`}
      />
      <Button variant="secondary" onClick={copy} className="shrink-0">
        {copied ? '복사됨' : '복사'}
      </Button>
    </div>
  )
}
