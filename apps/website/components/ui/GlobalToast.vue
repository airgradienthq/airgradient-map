<template>
  <v-snackbar
    v-if="toast"
    v-model="toast.show"
    :color="getColor(toast?.type)"
    location="top right"
    timeout="5000"
    @update:model-value="onUpdate"
  >
    <div class="d-flex align-center">
      <v-icon :icon="getIcon(toast?.type)" class="mr-2" />
      {{ toast?.message }}
    </div>
    
    <template #actions>
      <v-btn icon="mdi-close" variant="text" @click="hideToast" />
    </template>
  </v-snackbar>
</template>

<script setup lang="ts">
import { useToast } from '~/composables/useToast'

const { toast, hideToast } = useToast()

const getColor = (type?: string) => {
  switch (type) {
    case 'success': return 'success'
    case 'error': return 'error'
    case 'warning': return 'warning'
    default: return 'info'
  }
}

const getIcon = (type?: string) => {
  switch (type) {
    case 'success': return 'mdi-check-circle'
    case 'error': return 'mdi-alert-circle'
    case 'warning': return 'mdi-alert'
    default: return 'mdi-information'
  }
}

const onUpdate = (value: boolean) => {
  if (!value) hideToast()
}
</script>