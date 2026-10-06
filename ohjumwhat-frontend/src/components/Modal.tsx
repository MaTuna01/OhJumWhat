import { type ReactNode, useEffect, useId, useRef } from 'react'

type Props = {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
  /** md: 기본(폭 448px), lg: 큰 지도처럼 넓은 내용(폭 1024px) */
  size?: 'md' | 'lg'
  /** 제목 오른쪽에 ✕(닫기) 버튼을 둔다(취소 버튼이 없는 보기 전용 모달) */
  closable?: boolean
}

/** 네이티브 <dialog> 모달. Esc와 바깥 클릭으로 닫힌다. 닫혀 있을 때는 내용을 렌더링하지 않아 입력 상태가 초기화된다. */
export default function Modal({ open, onClose, title, children, size = 'md', closable = false }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  // 바깥에서 누르고 바깥에서 뗐을 때만 닫는다. 안에서 끌다가(사진 맞추기, 글자 선택) 바깥에서 떼도 닫히지 않게 한다.
  const pressedOutside = useRef(false)
  // 모달 두 개가 함께 열릴 수 있어서(채팅 시트 위의 프로필 등) 제목 id를 모달마다 따로 만든다.
  const titleId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      // React는 안쪽 <dialog>(프로필 모달 안의 지우기 확인·신고)의 close 이벤트도 여기로 올려 보내므로 자기 자신이 닫힐 때만 받는다.
      onClose={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
      onPointerDown={(e) => {
        pressedOutside.current = e.target === ref.current
      }}
      onClick={(e) => {
        if (e.target === ref.current && pressedOutside.current) onClose()
      }}
      aria-labelledby={titleId}
      className={`m-auto w-[calc(100%-2rem)] ${size === 'lg' ? 'max-w-5xl' : 'max-w-md'} rounded-2xl bg-bg-surface p-0 text-text-primary shadow-xl backdrop:bg-bg-scrim`}
    >
      {open && (
        <div className="p-5">
          <div className="flex items-start justify-between gap-3">
            <h2 id={titleId} className="text-lg font-bold">
              {title}
            </h2>
            {closable && (
              <button
                type="button"
                onClick={onClose}
                aria-label="닫기"
                className="-m-1 shrink-0 rounded-lg p-1 text-lg leading-none font-bold text-text-tertiary hover:bg-bg-subtle hover:text-text-secondary focus-visible:outline-2 focus-visible:outline-border-brand"
              >
                ✕
              </button>
            )}
          </div>
          <div className="mt-4">{children}</div>
        </div>
      )}
    </dialog>
  )
}
