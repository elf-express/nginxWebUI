<script setup lang="ts">
import { reactive, watch } from 'vue'
import { Form, FormItem, Input, Modal, Textarea, message } from 'ant-design-vue'
import { t } from '../../shared/i18n'
import { validateBasicForm, type BasicForm } from './form'

const props = defineProps<{ open: boolean; title: string; initial: BasicForm }>()
const emit = defineEmits<{ submit: [form: BasicForm]; cancel: [] }>()

const form = reactive<BasicForm>({ id: '', name: '', value: '' })

watch(
  () => props.open,
  (open) => {
    if (open) {
      Object.assign(form, props.initial)
    }
  },
  { immediate: true },
)

function onOk() {
  const errorKey = validateBasicForm(form)
  if (errorKey) {
    message.warning(t(errorKey))
    return
  }
  emit('submit', { ...form })
}
</script>

<template>
  <Modal
    :open="open"
    :title="title"
    :width="600"
    :ok-text="t('commonStr.submit')"
    :cancel-text="t('commonStr.close')"
    @ok="onOk"
    @cancel="emit('cancel')"
  >
    <Form layout="vertical">
      <FormItem :label="t('commonStr.name')" html-for="basic-form-name">
        <Input id="basic-form-name" v-model:value="form.name" />
      </FormItem>
      <FormItem :label="t('commonStr.value')" html-for="basic-form-value">
        <Textarea id="basic-form-value" v-model:value="form.value" :rows="4" />
      </FormItem>
    </Form>
  </Modal>
</template>
