<template>
  <ClientOnly>
    <Map />
  </ClientOnly>
</template>

<script setup lang="ts">
import { useToast } from '~/composables/useToast'
const { showSuccess, showError, showWarning, showInfo } = useToast()

const testSuccess = () => showSuccess('Success message!')
const testError = () => showError('Error message!')
const testWarning = () => showWarning('Warning message!')
const testInfo = () => showInfo('Info message!')
</script>