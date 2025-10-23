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

    <div v-if="showDropdown" class="layer-dropdown" @click.stop>
      <div class="layer-dropdown-header">
        <span>Map Layers</span>
      </div>

      <div class="layer-list">
        <div
          v-for="layer in layers"
          :key="layer.id"
          class="layer-item"
          :class="{ disabled: layer.disabled }"
        >
          <div class="layer-info">
            <div class="layer-icon">
              <i :class="layer.icon"></i>
            </div>
            <div class="layer-details">
              <span class="layer-name">{{ layer.name }}</span>
              <span v-if="layer.description" class="layer-description">{{
                layer.description
              }}</span>
            </div>
          </div>

          <div class="layer-controls">
            <div v-if="layer.loading" class="layer-loading">
              <i class="mdi mdi-loading mdi-spin"></i>
            </div>
            <label v-else class="layer-toggle">
              <input
                type="checkbox"
                :checked="layer.enabled"
                :disabled="layer.disabled"
                @change="toggleLayer(layer.id, ($event.target as HTMLInputElement).checked)"
              />
              <span class="toggle-slider"></span>
            </label>
          </div>
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

  function toggleLayer(layerId: string, enabled: boolean) {
    emit('layerToggle', layerId, enabled);
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

<style scoped>
  .layer-selector {
    position: relative;
  }

  .layer-dropdown {
    position: absolute;
    top: 100%;
    left: 0;
    margin-top: 4px;
    width: 300px;
    background: var(--main-white-color);
    border: 2px solid var(--grayColor400);
    border-radius: 20px;
    box-shadow: var(--shadow-primary);
    z-index: 1000;
    overflow: hidden;
  }

  .layer-dropdown-header {
    padding: 8px 20px;
    background: var(--primary-color);
    color: var(--main-white-color);
    font-weight: var(--font-weight-medium);
    font-size: var(--font-size-base);
    text-align: center;
    border-radius: 18px 18px 0 0;
  }

  .layer-list {
    padding: 0;
  }

  .layer-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 20px;
    transition: background-color var(--main-transition);
    border-bottom: 1px solid var(--grayColor200);
  }

  .layer-item:hover:not(.disabled) {
    background: var(--primary-color);
    color: var(--main-white-color);
  }

  .layer-item:last-child {
    border-bottom: none;
    border-radius: 0 0 18px 18px;
  }

  .layer-item.disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .layer-info {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
  }

  .layer-icon {
    width: 24px;
    height: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--primary-color);
    font-size: 18px;
  }

  .layer-details {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .layer-name {
    font-weight: var(--font-weight-medium);
    color: var(--main-text-color);
    font-size: var(--font-size-base);
  }

  .layer-description {
    font-size: var(--font-size-sm);
    color: var(--grayColor600);
  }

  .layer-controls {
    display: flex;
    align-items: center;
  }

  .layer-loading {
    color: var(--primary-color);
    font-size: 16px;
  }

  .layer-toggle {
    position: relative;
    display: inline-block;
    width: 44px;
    height: 24px;
    cursor: pointer;
  }

  .layer-toggle input {
    opacity: 0;
    width: 0;
    height: 0;
  }

  .toggle-slider {
    position: absolute;
    cursor: pointer;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: var(--grayColor300);
    transition: var(--main-transition);
    border-radius: 24px;
  }

  .toggle-slider:before {
    position: absolute;
    content: '';
    height: 18px;
    width: 18px;
    left: 3px;
    bottom: 3px;
    background-color: var(--main-white-color);
    transition: var(--main-transition);
    border-radius: 50%;
  }

  .layer-toggle input:checked + .toggle-slider {
    background-color: var(--primary-color);
  }

  .layer-toggle input:focus + .toggle-slider {
    box-shadow: 0 0 1px var(--primary-color);
  }

  .layer-toggle input:checked + .toggle-slider:before {
    transform: translateX(20px);
  }

  .layer-toggle input:disabled + .toggle-slider {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .layer-dropdown {
    animation: dropdownFadeIn 0.2s ease-out;
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
    .layer-dropdown {
      width: 280px;
      left: -220px;
    }
  }
</style>
