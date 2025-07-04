<template>
  <div
    ref="elementRef"
    class="hover-text-reveal"
    :style="{ maxWidth: maxWidth }"
  >
    <slot />
  </div>
</template>

<script setup lang="ts">
  import { onMounted } from 'vue';
  import { useHoverTextReveal } from '~/composables/shared/ui/useHoverTextReveal';

  interface Props {
    revealDelay?: number;
    resetDelay?: number;
    transitionDuration?: number;
    translateX?: number;
    maxWidth?: string;
  }

  const props = withDefaults(defineProps<Props>(), {
    revealDelay: 100,
    resetDelay: 2000,
    transitionDuration: 300,
    translateX: -60,
    maxWidth: '200px'
  });

  const { elementRef, setupElement } = useHoverTextReveal({
    revealDelay: props.revealDelay,
    resetDelay: props.resetDelay,
    transitionDuration: props.transitionDuration,
    translateX: props.translateX
  });

  onMounted(() => {
    if (elementRef.value) {
      setupElement(elementRef.value);
    }
  });
</script>

<style scoped>
  .hover-text-reveal {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
</style> 