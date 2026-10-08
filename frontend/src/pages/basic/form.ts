export interface BasicForm {
  id: string
  name: string
  value: string
}

// 回傳錯誤訊息的 i18n key；通過時回 null
export function validateBasicForm(form: BasicForm): string | null {
  if (form.name.trim() === '') {
    return 'basicStr.nameNotice'
  }
  if (form.value.trim() === '') {
    return 'basicStr.valueNotice'
  }
  return null
}
