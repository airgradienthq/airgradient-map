<template>
  <v-snackbar
    v-model="isVisible"
    :timeout="toast?.timeout || 4000"
    :color="toastColor"
    :persistent="toast?.persistent"
    location="top right"
    elevation="6"
    rounded="lg"
    class="toast-snackbar"
    multi-line
  >
    <div class="d-flex align-center">
      <v-icon :icon="toastIcon" class="mr-3" size="20" />
      <span class="toast-message">{{ toast?.message }}</span>
    </div>
    
    <template v-slot:actions>
      <v-btn
        icon="mdi-close"
        size="small"
        variant="text"
        @click="hideToast"
        aria-label="Close notification"
      />
    </template>
  </v-snackbar>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useToast } from '~/composables/useToast';

const { toast, hideToast } = useToast();

const isVisible = computed({
  get: () => !!toast.value,
  set: (value) => {
    if (!value) hideToast();
  },
});

const toastColor = computed(() => {
  switch (toast.value?.type) {
    case 'success': return 'success';
    case 'error': return 'error';
    case 'warning': return 'warning';
    case 'info': 
    default: return 'primary';
  }
});

const toastIcon = computed(() => {
  switch (toast.value?.type) {
    case 'success': return 'mdi-check-circle';
    case 'error': return 'mdi-alert-circle';
    case 'warning': return 'mdi-alert';
    case 'info':
    default: return 'mdi-information';
  }
});
</script>

<style scoped>
.toast-snackbar {
  z-index: 9999;
}

.toast-message {
  font-weight: 500;
  line-height: 1.4;
  word-break: break-word;
}

.toast-snackbar :deep(.v-snackbar__wrapper) {
  pointer-events: auto;
  max-width: 400px;
}

@media (max-width: 600px) {
  .toast-snackbar :deep(.v-snackbar__wrapper) {
    max-width: 90vw;
    margin-left: 5vw;
    margin-right: 5vw;
  }
}
</style>