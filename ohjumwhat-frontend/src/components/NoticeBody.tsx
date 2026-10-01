import { Fragment } from 'react'
import { type NoticeLine, parseNoticeBody } from '../lib/noticeBody.ts'

function Line({ line }: { line: NoticeLine }) {
  return (
    <>
      {line.map((segment, i) =>
        segment.type === 'link' ? (
          <a
            key={i}
            href={segment.href}
            target="_blank"
            rel="noopener noreferrer"
            className="break-all text-text-brand underline underline-offset-2 hover:text-text-brand-strong focus-visible:outline-2 focus-visible:outline-border-brand"
          >
            {segment.text}
          </a>
        ) : (
          <Fragment key={i}>{segment.text}</Fragment>
        ),
      )}
    </>
  )
}

/** 새 소식 본문(일반 텍스트): 빈 줄 = 문단, "- " = 목록, http(s) 주소 = 새 탭 링크 */
export default function NoticeBody({ body }: { body: string }) {
  return (
    <div className="space-y-2 text-sm leading-relaxed text-text-secondary">
      {parseNoticeBody(body).map((block, i) =>
        block.type === 'list' ? (
          <ul key={i} className="list-disc space-y-1 pl-5 marker:text-text-tertiary">
            {block.items.map((item, j) => (
              <li key={j}>
                <Line line={item} />
              </li>
            ))}
          </ul>
        ) : (
          <p key={i}>
            {block.lines.map((line, j) => (
              <Fragment key={j}>
                {j > 0 && <br />}
                <Line line={line} />
              </Fragment>
            ))}
          </p>
        ),
      )}
    </div>
  )
}
