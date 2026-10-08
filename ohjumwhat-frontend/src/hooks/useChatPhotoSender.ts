import { useState } from 'react'
import { pickNotice, pickPhotos, preparePhoto, type Size } from '../lib/chatPhoto.ts'
import { useSendPhoto } from '../queries/chat.ts'

/** 보내는 중인 사진(말풍선 미리보기) */
export type PendingPhoto = Size & { key: string; previewUrl: string }

export type ChatPhotoSender = {
  /** 고른 사진을 앞에서 5장까지 한 장씩 차례로 보낸다(보내는 중이면 무시한다). */
  send: (files: File[]) => void
  pending: PendingPhoto[]
  busy: boolean
  /** 「사진 보내는 중 2/5」 */
  status: string | null
  /** 고른 뒤의 안내·실패 문구 */
  notice: { text: string; error: boolean } | null
}

/**
 * 채팅 사진 보내기(Figma 05-C8). 사진마다 브라우저에서 긴 변 1600px JPEG로 줄인 뒤(lib/chatPhoto.ts preparePhoto) 메시지 하나로 보낸다.
 * 열 수 없는 사진은 건너뛰고, 서버가 거절하면(채팅이 닫힘·속도 제한·이용 제한) 남은 사진은 보내지 않는다.
 * beforeEach: 사진마다 보내기 전에 부른다(ChatPanel이 목록을 맨 아래에 붙인다).
 */
export function useChatPhotoSender(pollId: number, beforeEach: () => void): ChatPhotoSender {
  const sendPhoto = useSendPhoto(pollId)
  const [pending, setPending] = useState<PendingPhoto[]>([])
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null)
  const [notice, setNotice] = useState<ChatPhotoSender['notice']>(null)

  const send = async (files: File[]) => {
    if (progress) return
    const picked = pickPhotos(files)
    const pickText = pickNotice(picked)
    setNotice(pickText ? { text: pickText, error: false } : null)
    const total = picked.photos.length
    if (total === 0) return
    let unreadable = 0
    try {
      for (const [i, file] of picked.photos.entries()) {
        setProgress({ done: i, total })
        let prepared
        try {
          prepared = await preparePhoto(file)
        } catch {
          unreadable++
          continue
        }
        const key = `${Date.now()}-${i}`
        beforeEach()
        setPending((list) => [...list, { key, previewUrl: prepared.previewUrl, width: prepared.width, height: prepared.height }])
        try {
          await sendPhoto.mutateAsync(prepared.blob)
        } catch (e) {
          setNotice({ text: e instanceof Error ? e.message : '사진을 보내지 못했어요.', error: true })
          return
        } finally {
          setPending((list) => list.filter((p) => p.key !== key))
        }
      }
      if (unreadable > 0) {
        setNotice({
          text: unreadable === total ? '이 사진 형식은 열 수 없어요. JPG나 PNG 사진을 골라 주세요.' : `열 수 없는 사진 ${unreadable}장은 빼고 보냈어요.`,
          error: true,
        })
      }
    } finally {
      setProgress(null)
    }
  }

  return {
    send: (files) => void send(files),
    pending,
    busy: progress != null,
    status: progress ? `사진 보내는 중 ${progress.done + 1}/${progress.total}` : null,
    notice,
  }
}
