<template>
  <div class="ag-dropdown-wrapper" @click="toggleMenu">
    <!-- YOUR EXACT ORIGINAL BUTTON -->
    <div 
      class="ag-dropdown-control__trigger"
      :class="{
        'ag-dropdown-control__trigger--disabled': disabled,
        'ag-dropdown-control__trigger--open': isMenuOpen,
        'ag-dropdown-control__trigger--small': size === 'small'
      }"
    >
      <span>{{ displayValue || placeholder }}</span>
      <svg width="20" height="20" viewBox="0 0 24 24" :class="{ rotate: isMenuOpen }">
        <path d="M8 5V19L19 12L8 5Z" fill="currentColor" />
      </svg>
    </div>

    <!-- HIDDEN VUETIFY FOR FUNCTIONALITY -->
    <v-select
      ref="selectRef"
      :model-value="selectedValue"
      :items="options"
      :placeholder="placeholder"
      :disabled="disabled"
      class="ag-dropdown-hidden"
      item-title="label"
      item-value="value"
      variant="outlined"
      hide-details
      :menu-props="{
        contentClass: 'ag-dropdown-menu',
        location: 'bottom',
        origin: 'top',
        offset: 4
      }"
      @update:model-value="handleChange"
      @update:menu="isMenuOpen = $event"
    />
  </div>
</template>

<script setup lang="ts">
  import { PropType, ref, computed } from 'vue';
  import { DropdownOption, DropdownSize } from '~/types';

  const props = defineProps({
    options: {
      type: Array as PropType<Array<DropdownOption>>,
      required: true
    },
    selectedValue: {
      type: [String, Number],
      default: ''
    },
    size: {
      type: String as PropType<DropdownSize>,
      default: DropdownSize.SMALL
    },
    disabled: {
      type: Boolean,
      default: false
    },
    placeholder: {
      type: String,
      default: 'Select an option...'
    }
  });

  const emit = defineEmits(['update:modelValue', 'change']);
  const selectRef = ref();
  const isMenuOpen = ref(false);

  const displayValue = computed(() => {
    const selected = props.options.find(option => option.value === props.selectedValue);
    return selected?.label || null;
  });

  const handleChange = (value: string | number) => {
    emit('update:modelValue', value);
    emit('change', value);
  };

  const toggleMenu = () => {
    if (!props.disabled && selectRef.value) {
      selectRef.value.menu = !selectRef.value.menu;
    }
  };
</script>

<style lang="scss">
.ag-dropdown-wrapper {
  position: relative;
  width: 100%;
}

/* Clean button styling - only visual overrides */
.ag-dropdown-control__trigger {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 20px;
  background-color: var(--main-white-color);
  border: 2px solid var(--grayColor400);
  border-radius: 100px;
  cursor: pointer;
  transition: var(--main-transition);
  min-height: 39px;

  &:hover:not(&--disabled) {
    background-color: var(--primary-color);
    color: var(--main-white-color);
    border-color: var(--primary-color);
  }

  &--open {
    background-color: var(--primary-color);
    color: var(--main-white-color);
    border-color: var(--primary-color);
  }

  &--disabled {
    cursor: not-allowed;
    color: var(--main-disabled-color);
    border-color: var(--main-disabled-color);

    &:hover {
      background-color: var(--main-white-color);
      color: var(--main-disabled-color);
      border-color: var(--main-disabled-color);
    }
  }

  &--small {
    padding: 8px 16px;
    min-height: 35px;
  }

  svg {
    transform: rotate(90deg);
    transition: var(--main-transition);
    flex-shrink: 0;
  }

  .rotate {
    transform: rotate(270deg);
  }
}

/* Hide Vuetify completely */
.ag-dropdown-hidden {
  position: absolute !important;
  top: 0 !important;
  left: 0 !important;
  width: 100% !important;
  height: 100% !important;
  opacity: 0 !important;
  pointer-events: none !important;
  z-index: -1 !important;
}

/* Clean menu styling - only visual overrides */
.ag-dropdown-menu {
  margin-top: 4px !important;
  background-color: var(--main-white-color) !important;
  border: 2px solid var(--grayColor400) !important;
  border-radius: 20px !important;
  box-shadow: var(--shadow-primary) !important;
  max-height: 300px !important;
  overflow-y: auto !important;
  overflow-x: hidden !important;
  padding: 0 !important;

  .v-list-item {
    padding: 12px 20px !important;
    cursor: pointer !important;
    transition: var(--main-transition) !important;
    border-bottom: 1px solid var(--grayColor200) !important;
    border-radius: 0 !important;
    min-height: auto !important;
    white-space: nowrap !important;
    overflow: hidden !important;
    text-overflow: ellipsis !important;

    &:first-child {
      border-radius: 18px 18px 0 0 !important;
    }

    &:last-child {
      border-bottom: none !important;
      border-radius: 0 0 18px 18px !important;
    }

    &:only-child {
      border-radius: 18px !important;
      border-bottom: none !important;
    }

    &:hover {
      background-color: var(--primary-color) !important;
      color: var(--main-white-color) !important;
    }

    &.v-list-item--active {
      background-color: var(--light-primary-color) !important;
      color: var(--primary-color) !important;
      font-weight: var(--font-weight-bold) !important;
    }
  }
}
</style>