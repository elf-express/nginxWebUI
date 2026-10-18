<script setup lang="ts">
import { computed } from 'vue'
import { Switch, Table, Tag, message } from 'ant-design-vue'
import { t, te } from '../../shared/i18n'
import { notifyError } from '../../shared/notify'
import { useBasicStore, type Module } from './store'

const store = useBasicStore()
const onDisk = computed(() => new Set(store.modulesOnDisk))

const columns = computed(() => [
  { title: t('moduleStr.moduleName'), key: 'name', width: 300 },
  { title: t('moduleStr.description'), key: 'description' },
  { title: t('moduleStr.status'), key: 'status', width: 100 },
])

function description(module: Module): string {
  const key = `moduleStr.${module.descrKey}`
  return te(key) ? t(key) : ''
}

async function toggle(module: Module, checked: boolean) {
  try {
    const cascaded = await store.toggleModule(module.name, checked)
    for (const name of cascaded) {
      message.info(t(checked ? 'moduleStr.depAutoEnabled' : 'moduleStr.depAutoDisabled', name))
    }
  } catch (err) {
    notifyError(err)
  }
}
</script>

<template>
  <section class="module-section">
    <h2 class="module-title">{{ t('moduleStr.title') }}</h2>
    <Table
      class="module-table"
      row-key="id"
      size="small"
      :columns="columns"
      :data-source="store.moduleList"
      :pagination="false"
      :locale="{ emptyText: t('commonStr.noData') }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <code>{{ record.name }}</code>
          <Tag v-if="!onDisk.has(record.name)" class="module-tag">{{ t('moduleStr.notOnDisk') }}</Tag>
        </template>
        <template v-else-if="column.key === 'description'">{{ description(record as Module) }}</template>
        <template v-else-if="column.key === 'status'">
          <Switch
            v-if="onDisk.has(record.name)"
            :checked="!!record.enable"
            :aria-label="record.name"
            @change="(checked: unknown) => toggle(record as Module, checked === true)"
          />
          <Tag v-else>N/A</Tag>
        </template>
      </template>
    </Table>
  </section>
</template>

<style scoped>
.module-section {
  margin-top: 30px;
}
.module-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0 0 10px;
}
.module-tag {
  margin-left: 5px;
}
</style>
