import { Link } from 'react-router'
import { LogoMark } from '../components/Logo.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { buttonClass } from '../lib/ui.ts'

/** 없는 주소로 들어왔을 때 */
export default function NotFoundPage() {
  useDocumentTitle('페이지를 찾을 수 없어요')
  return (
    <div className="flex flex-col items-center gap-3 py-16 text-center">
      <LogoMark size={56} />
      <h1 className="mt-2 text-xl font-bold">페이지를 찾을 수 없어요</h1>
      <p className="text-sm text-text-tertiary">주소가 잘못됐거나 사라진 페이지예요.</p>
      <Link to="/" className={buttonClass('primary', 'mt-4')}>
        처음으로
      </Link>
    </div>
  )
}
