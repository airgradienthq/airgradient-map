<template>
  <div class="layer-selector">
    <UiIconButton
      :ripple="false"
      :size="ButtonSize.NORMAL"
      icon="mdi-layers"
      :style="'light'"
      title="Toggle Layers"
      @click="toggleDropdown"
    />

    <div v-if="showDropdown" class="ag-dropdown-menu" @click.stop>
      <div
        v-for="layer in layers"
        :key="layer.id"
        class="layer-item"
        :class="{ 
          disabled: layer.disabled,
          active: layer.enabled
        }"
        @click="toggleLayer(layer.id)"
      >
        <span class="layer-name">{{ layer.name }}</span>
        <div v-if="layer.loading" class="layer-loading">
          <i class="mdi mdi-loading mdi-spin"></i>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted, onUnmounted } from 'vue';
  import { ButtonSize } from '~/types';

  interface LayerConfig {
    id: string;
    name: string;
    description?: string;
    icon: string;
    enabled: boolean;
    disabled?: boolean;
    loading?: boolean;
  }

  interface Props {
    layers: LayerConfig[];
  }

  interface Emits {
    layerToggle: [layerId: string, enabled: boolean];
  }

  const props = defineProps<Props>();
  const emit = defineEmits<Emits>();

  const showDropdown = ref(false);

  function toggleDropdown() {
    showDropdown.value = !showDropdown.value;
  }

  function toggleLayer(layerId: string) {
    const layer = props.layers.find(l => l.id === layerId);
    if (layer && !layer.disabled && !layer.loading) {
      emit('layerToggle', layerId, !layer.enabled);
      showDropdown.value = false;
    }
  }

  function handleClickOutside(event: Event) {
    const target = event.target as HTMLElement;
    const dropdown = document.querySelector('.layer-selector');

    if (dropdown && !dropdown.contains(target)) {
      showDropdown.value = false;
    }
  }

  onMounted(() => {
    document.addEventListener('click', handleClickOutside);
  });

  onUnmounted(() => {
    document.removeEventListener('click', handleClickOutside);
  });
</script>

<style lang="scss">
  .layer-selector {
    position: relative;
  }

  .ag-dropdown-menu {
    position: absolute;
    top: 100%;
    left: 0;
    margin-top: 4px;
    width: 220px;
    background-color: var(--main-white-color);
    border: 2px solid var(--grayColor400);
    border-radius: 20px;
    box-shadow: var(--shadow-primary);
    z-index: 1000;
    overflow: hidden;
    font-family: var(--secondary-font);
    font-weight: var(--font-weight-medium);
    animation: dropdownFadeIn 0.2s ease-out;
  }

  .layer-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 16px;
    cursor: pointer;
    transition: var(--main-transition);
    border-bottom: 1px solid var(--grayColor200);
    border-radius: 0;
    min-height: auto;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;

    &:first-child {
      border-radius: 18px 18px 0 0;
    }

    &:last-child {
      border-bottom: none;
      border-radius: 0 0 18px 18px;
    }

    &:only-child {
      border-radius: 18px;
      border-bottom: none;
    }

    &:hover:not(.disabled) {
      background-color: var(--primary-color);
      color: var(--main-white-color);
      border-color: transparent;
    }

    &.active {
      background-color: var(--light-primary-color);
      color: var(--primary-color);
      font-weight: var(--font-weight-bold);
      border-color: transparent;
    }

    &.disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  .layer-name {
    font-size: var(--font-size-base);
  }

  .layer-loading {
    color: var(--primary-color);
    font-size: 16px;
  }

  @keyframes dropdownFadeIn {
    from {
      opacity: 0;
      transform: translateY(-10px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  @media (max-width: 480px) {
    .ag-dropdown-menu {
      width: 200px;
      left: -140px;
    }
  }
</style>