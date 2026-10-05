import { linkify } from '../lib/noticeBody.ts'
import { LinkedLine } from './NoticeBody.tsx'

/** 메시지 본문(채팅·쪽지): 줄바꿈을 살리고 http(s) 주소는 새 탭 링크 */
export default function MessageBody({ body }: { body: string }) {
  return (
    <>
      {body.split('\n').map((line, i) => (
        <span key={i}>
          {i > 0 && <br />}
          <LinkedLine line={linkify(line)} />
        </span>
      ))}
    </>
  )
}
