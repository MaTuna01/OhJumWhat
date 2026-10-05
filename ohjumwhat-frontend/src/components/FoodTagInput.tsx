import { FOOD_TAG_RULE, type TagState } from '../lib/profile.ts'
import TagInput from './TagInput.tsx'

export type FoodTagState = TagState

type Props = {
  id: string
  value: FoodTagState
  onChange: (value: FoodTagState) => void
  disabled?: boolean
}

/** 좋아하는 음식 태그 입력(Figma 03-M2 「좋아하는 음식」, 10자·3개). 동작은 TagInput이 맡는다. */
export default function FoodTagInput({ id, value, onChange, disabled }: Props) {
  return (
    <TagInput id={id} value={value} onChange={onChange} disabled={disabled} rule={FOOD_TAG_RULE} placeholder="음식 이름을 적고 Enter" />
  )
}
