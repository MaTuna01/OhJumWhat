import { describe, expect, it } from 'vitest'
import type { Me } from '../queries/me.ts'
import { canRevertPhoto, nicknameChange, type PendingPhoto, photoChange, photoFileError, previewPhoto, revertPhoto } from './profile.ts'

const me: Me = {
  id: 1,
  name: '김철수',
  nickname: null,
  googleName: '김철수',
  email: 'kim@example.com',
  profileImageUrl: 'https://lh3.googleusercontent.com/kim',
  googleProfileImageUrl: 'https://lh3.googleusercontent.com/kim',
  customPhoto: false,
  lastVisitedOrgId: 1,
  admin: false,
}
const withPhoto: Me = { ...me, profileImageUrl: '/api/photos/abc.jpg', customPhoto: true }
const none: PendingPhoto = { kind: 'none' }
const upload: PendingPhoto = { kind: 'upload', blob: new Blob(), dataUrl: 'data:image/jpeg;base64,AAAA' }
const reset: PendingPhoto = { kind: 'reset' }

describe('사진', () => {
  it('미리보기는 방금 고른 사진, 되돌릴 구글 사진, 지금 사진 순이다', () => {
    expect(previewPhoto(withPhoto, upload)).toBe('data:image/jpeg;base64,AAAA')
    expect(previewPhoto(withPhoto, reset)).toBe('https://lh3.googleusercontent.com/kim')
    expect(previewPhoto(withPhoto, none)).toBe('/api/photos/abc.jpg')
  })

  it('되돌리기는 올린 사진이나 방금 고른 사진이 있을 때만 보인다', () => {
    expect(canRevertPhoto(me, none)).toBe(false)
    expect(canRevertPhoto(me, upload)).toBe(true)
    expect(canRevertPhoto(withPhoto, none)).toBe(true)
    expect(canRevertPhoto(withPhoto, reset)).toBe(false)
  })

  it('올린 사진이 있으면 지우기로, 없으면 고르기 전으로 되돌린다', () => {
    expect(revertPhoto(withPhoto)).toEqual(reset)
    expect(revertPhoto(me)).toEqual(none)
  })

  it('저장할 변경은 올리기·지우기만 있다', () => {
    expect(photoChange(none)).toBeNull()
    expect(photoChange(reset)).toEqual({ kind: 'reset' })
    expect(photoChange(upload)).toEqual({ kind: 'upload', blob: (upload as { blob: Blob }).blob })
  })

  it('사진이 아니거나 20MB가 넘는 파일은 고를 수 없다', () => {
    expect(photoFileError({ type: 'application/pdf', size: 10 })).toBe('사진 파일만 올릴 수 있어요.')
    expect(photoFileError({ type: 'image/heic', size: 21 * 1024 * 1024 })).toBe('20MB보다 작은 사진을 골라 주세요.')
    expect(photoFileError({ type: 'image/jpeg', size: 3 * 1024 * 1024 })).toBeNull()
  })
})

describe('nicknameChange', () => {
  it('화면 이름과 같으면 바뀌지 않은 것이다', () => {
    expect(nicknameChange(me, ' 김철수 ').changed).toBe(false)
    expect(nicknameChange(me, '').changed).toBe(false)
  })

  it('새 이름은 공백을 정리하고, 별명이 있을 때 비우면 구글 이름으로 돌아간다', () => {
    expect(nicknameChange(me, '  점심   요정 ')).toEqual({ nickname: '점심 요정', changed: true, length: 5 })
    expect(nicknameChange({ ...me, name: '점심요정', nickname: '점심요정' }, '  ')).toEqual({ nickname: null, changed: true, length: 0 })
  })

  it('글자 수는 이모지도 한 글자로 센다', () => {
    expect(nicknameChange(me, '😀'.repeat(20)).length).toBe(20)
  })
})
