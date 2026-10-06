import { type ReactNode, useId } from 'react'
import { usePushDevice } from '../hooks/usePushDevice.ts'
import { type PushWebConfig, useClientConfig } from '../queries/config.ts'
import { Section } from './PageState.tsx'
import Switch from './Switch.tsx'

/**
 * 마이페이지 「알림」 카드(Figma PushSettingsCard, 03-N6·D03-N6). 이 기기에서 웹 푸시를 켜고 끈다.
 * 서버에서 푸시를 껐으면(GET /api/config의 push가 null) 카드 자체를 숨긴다.
 */
export default function NotificationSettings({ userId, className }: { userId: number; className?: string }) {
  const { data: config } = useClientConfig()
  const push = config?.push ?? null
  if (!push) return null
  return <NotificationCard userId={userId} push={push} className={className} />
}

function NotificationCard({ userId, push, className }: { userId: number; push: PushWebConfig; className?: string }) {
  const { state, pending, enable, disable } = usePushDevice(userId, push)
  const id = useId()
  const locked = state === 'checking' || state === 'unsupported' || state === 'ios-install' || state === 'ios-inapp' || state === 'denied'

  return (
    <Section title="알림" className={className}>
      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1 space-y-0.5">
            <p id={`${id}-label`} className="text-sm font-medium">
              이 기기에서 알림 받기
            </p>
            <p id={`${id}-description`} className="text-xs text-text-tertiary">
              새 방명록·쪽지가 오면 이 기기로 알려 드려요. 알림에는 보낸 사람 이름만 보여요.
            </p>
          </div>
          <Switch
            checked={pending ?? state === 'on'}
            onChange={(on) => void (on ? enable() : disable())}
            disabled={locked || pending !== null}
            labelledBy={`${id}-label`}
            describedBy={`${id}-description`}
          />
        </div>

        {(state === 'ios-install' || state === 'ios-inapp') && (
          <Notice title="홈 화면에 추가하면 알림을 받을 수 있어요">
            {state === 'ios-inapp' && '앱 안의 브라우저에서는 홈 화면에 추가할 수 없으니 먼저 Safari로 열어 주세요. '}
            {'아이폰·아이패드는 홈 화면에 추가한 오점왓에서만 알림을 받을 수 있어요(iOS 16.4 이상). Safari 아래쪽 공유 버튼 → 「홈 화면에 추가」를 누른 뒤, 홈 화면의 오점왓에서 다시 로그인하고 켜 주세요.'}
          </Notice>
        )}
        {state === 'denied' && (
          <Notice title="브라우저에서 알림이 차단돼 있어요" warning>
            주소창 왼쪽의 사이트 설정에서 알림을 「허용」으로 바꾼 뒤 새로 고침하고 다시 켜 주세요.
          </Notice>
        )}
        {state === 'unsupported' && (
          <Notice title="이 브라우저에서는 알림을 받을 수 없어요">
            크롬·엣지·파이어폭스나 Safari(macOS)에서 열면 알림을 켤 수 있어요. 새 소식은 화면 위쪽 배지로 계속 알려 드려요.
          </Notice>
        )}
        {state === 'on' && pending === null && <p className="text-xs text-text-success">이 기기에서 알림을 받고 있어요.</p>}
        {state === 'error' && pending === null && (
          <p role="alert" className="text-xs text-text-danger">
            알림을 켜지 못했어요. 잠시 후 다시 시도해 주세요.
          </p>
        )}
      </div>
    </Section>
  )
}

/** 스위치를 쓸 수 없을 때의 안내 상자(Figma PushSettingsCard Notice) */
function Notice({ title, warning = false, children }: { title: string; warning?: boolean; children: ReactNode }) {
  return (
    <div className={`space-y-1 rounded-xl px-3.5 py-3 ${warning ? 'bg-bg-warning-soft' : 'bg-bg-subtle'}`}>
      <p className={`text-sm font-medium ${warning ? 'text-text-warning' : ''}`}>{title}</p>
      <p className="text-xs text-text-secondary">{children}</p>
    </div>
  )
}
