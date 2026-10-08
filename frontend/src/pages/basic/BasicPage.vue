<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Alert, Breadcrumb, BreadcrumbItem, Button, ConfigProvider, Modal, Space, Table, message } from 'ant-design-vue'
import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { SessionExpiredError } from '../../shared/http'
import { t } from '../../shared/i18n'
import { notifyError } from '../../shared/notify'
import BasicFormModal from './BasicFormModal.vue'
import ModuleTable from './ModuleTable.vue'
import type { BasicForm } from './form'
import { useBasicStore } from './store'

const store = useBasicStore()
const loadError = ref(false)
const selectedIds = ref<string[]>([])
const submitting = ref(false)
const modal = reactive<{ open: boolean; title: string; initial: BasicForm }>({
  open: false,
  title: '',
  initial: { id: '', name: '', value: '' },
})

// 主色對齊舊版 Layui 的 layui-btn-normal
const theme = { token: { colorPrimary: '#1e9fff' } }

const columns = computed(() => [
  { title: t('commonStr.name'), dataIndex: 'name', key: 'name' },
  { title: t('commonStr.value'), dataIndex: 'value', key: 'value', width: '50%' },
  { title: t('commonStr.operation'), key: 'operation' },
])

const rowSelection = computed(() => ({
  selectedRowKeys: selectedIds.value,
  onChange: (keys: (string | number)[]) => {
    selectedIds.value = keys.map(String)
  },
}))

async function reload() {
  loadError.value = false
  try {
    await store.load()
  } catch (err) {
    if (err instanceof SessionExpiredError) {
      return
    }
    console.error(err)
    loadError.value = true
  }
}

function openAdd() {
  modal.initial = { id: '', name: '', value: '' }
  modal.title = t('basicStr.add')
  modal.open = true
}

async function openEdit(id: string) {
  try {
    const basic = await store.detail(id)
    modal.initial = { id: basic.id, name: basic.name, value: basic.value }
    modal.title = t('basicStr.edit')
    modal.open = true
  } catch (err) {
    notifyError(err)
  }
}

async function onSubmit(form: BasicForm) {
  if (submitting.value) {
    return
  }
  submitting.value = true
  try {
    await store.save(form)
    modal.open = false
  } catch (err) {
    notifyError(err)
    return
  } finally {
    submitting.value = false
  }
  await reload()
}

function confirmDelete(ids: string[]) {
  Modal.confirm({
    title: t('commonStr.confirmDel'),
    okText: t('commonStr.submit'),
    cancelText: t('commonStr.close'),
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await store.remove(ids)
      } catch (err) {
        notifyError(err)
        return
      }
      selectedIds.value = selectedIds.value.filter((id) => !ids.includes(id))
      await reload()
    },
  })
}

function deleteSelected() {
  if (selectedIds.value.length === 0) {
    message.warning(t('commonStr.unselected'))
    return
  }
  confirmDelete([...selectedIds.value])
}

async function move(id: string, count: -1 | 1) {
  try {
    await store.move(id, count)
  } catch (err) {
    notifyError(err)
    return
  }
  await reload()
}

onMounted(reload)
</script>

<template>
  <ConfigProvider :theme="theme">
    <div class="spa-page">
      <h1 class="spa-title">{{ t('menuStr.basic') }}</h1>
      <Breadcrumb class="spa-breadcrumb">
        <BreadcrumbItem><a href="/adminPage/monitor">{{ t('commonStr.home') }}</a></BreadcrumbItem>
        <BreadcrumbItem>{{ t('menuStr.basic') }}</BreadcrumbItem>
      </Breadcrumb>

      <Alert v-if="loadError" type="error" show-icon :message="t('spaStr.loadFailed')">
        <template #action>
          <Button size="small" @click="reload">{{ t('spaStr.reload') }}</Button>
        </template>
      </Alert>

      <template v-else>
        <Space class="spa-toolbar" wrap>
          <Button type="primary" @click="openAdd">
            <template #icon><PlusOutlined /></template>
            {{ t('basicStr.add') }}
          </Button>
          <Button danger @click="deleteSelected">
            <template #icon><DeleteOutlined /></template>
            {{ t('commonStr.delAll') }}
          </Button>
          <a class="spa-legacy-link" href="?legacy=1">{{ t('spaStr.legacyLink') }}</a>
        </Space>

        <Table
          class="basic-table"
          row-key="id"
          size="small"
          :columns="columns"
          :data-source="store.basicList"
          :loading="!store.loaded"
          :pagination="false"
          :row-selection="rowSelection"
          :locale="{ emptyText: t('commonStr.noData') }"
        >
          <template #bodyCell="{ column, record }">
            <Space v-if="column.key === 'operation'" wrap>
              <Button size="small" @click="openEdit(record.id)">
                <template #icon><EditOutlined /></template>
                {{ t('commonStr.edit') }}
              </Button>
              <Button size="small" danger @click="confirmDelete([record.id])">
                <template #icon><DeleteOutlined /></template>
                {{ t('commonStr.del') }}
              </Button>
              <Button size="small" @click="move(record.id, -1)">
                <template #icon><ArrowUpOutlined /></template>
                {{ t('commonStr.up') }}
              </Button>
              <Button size="small" @click="move(record.id, 1)">
                <template #icon><ArrowDownOutlined /></template>
                {{ t('commonStr.down') }}
              </Button>
            </Space>
          </template>
        </Table>

        <ModuleTable v-if="store.isLinux" />
      </template>

      <BasicFormModal
        :open="modal.open"
        :title="modal.title"
        :initial="modal.initial"
        :submitting="submitting"
        @submit="onSubmit"
        @cancel="modal.open = false"
      />
    </div>
  </ConfigProvider>
</template>

<style scoped>
.spa-page {
  padding: 15px;
}
.spa-title {
  font-size: 18px;
  font-weight: 500;
  margin: 0 0 8px;
}
.spa-breadcrumb {
  margin-bottom: 15px;
}
.spa-toolbar {
  margin-bottom: 12px;
}
.spa-legacy-link {
  margin-left: 8px;
}
</style>
