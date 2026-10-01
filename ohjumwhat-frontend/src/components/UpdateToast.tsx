import { useNewBuild } from '../queries/build.ts'
import Button from './Button.tsx'

/** 새 버전 안내(Figma 「디자인 시스템」 UpdateToast). 화면을 가리지 않게 아래 가운데에 띄운다. */
export default function UpdateToast() {
  const outdated = useNewBuild()
  if (!outdated) return null

  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-4 z-30 flex justify-center px-4">
      <div
        role="status"
        className="pointer-events-auto flex items-center gap-3 rounded-xl border border-border-default bg-bg-surface py-2 pr-2 pl-4 shadow-lg"
      >
        <p className="text-sm font-medium">오점왓이 업데이트됐어요</p>
        <Button onClick={() => window.location.reload()}>새로고침</Button>
      </div>
    </div>
  )
}
