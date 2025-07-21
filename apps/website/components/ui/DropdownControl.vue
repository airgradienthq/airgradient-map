<template>
  <div ref="dropdownRef" class="ag-dropdown-control">
    <button
      class="ag-dropdown-control__trigger"
      :class="{
        'ag-dropdown-control__trigger--disabled': disabled,
        'ag-dropdown-control__trigger--open': isOpen,
        'ag-dropdown-control__trigger--small': size === 'small'
      }"
      :disabled="disabled"
      @click="toggleDropdown"
    >
      <span>{{ displayValue || placeholder }}</span>
      <svg width="20" height="20" viewBox="0 0 24 24" :class="{ rotate: isOpen }">
        <path d="M8 5V19L19 12L8 5Z" fill="currentColor" />
      </svg>
    </button>

    <div v-if="isOpen" class="ag-dropdown-control__menu">
      <div
        v-for="option in options"
        :key="option.value"
        class="ag-dropdown-control__option"
        :class="{ 'ag-dropdown-control__option--selected': option.value === selectedValue }"
        @click="selectOption(option)"
      >
        {{ option.label }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { PropType, ref, computed, onMounted, onUnmounted } from 'vue';
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

  const dropdownRef = ref<HTMLElement>();
  const isOpen = ref(false);

  const displayValue = computed(() => {
    const selected = props.options.find(option => option.value === props.selectedValue);
    return selected?.label || null;
  });

  const toggleDropdown = () => {
    if (!props.disabled) {
      isOpen.value = !isOpen.value;
    }
  };

  const selectOption = (option: DropdownOption) => {
    emit('update:modelValue', option.value);
    emit('change', option.value);
    isOpen.value = false;
  };

  const handleClickOutside = (event: Event) => {
    if (dropdownRef.value && !dropdownRef.value.contains(event.target as Node)) {
      isOpen.value = false;
    }
  };

  onMounted(() => document.addEventListener('click', handleClickOutside));
  onUnmounted(() => document.removeEventListener('click', handleClickOutside));
</script>

<style lang="scss">
  .ag-dropdown-control {
    position: relative;
    width: 100%;
    font-family: var(--secondary-font);

    &__trigger {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 11px 20px;
      background-color: var(--main-white-color);
      border: 2px solid var(--grayColor400);
      border-radius: 100px;
      font-family: var(--secondary-font);
      font-weight: var(--font-weight-medium);
      font-size: 14px;
      line-height: 17px;
      color: var(--main-text-color);
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
        font-size: 14px;
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

    &__menu {
      position: absolute;
      top: 100%;
      left: 0;
      right: 0;
      z-index: 1000;
      margin-top: 4px;
      background-color: var(--main-white-color);
      border: 2px solid var(--grayColor400);
      border-radius: 20px;
      box-shadow: var(--shadow-primary);
      max-height: 300px;
      overflow-y: auto;
    }

    &__option {
      padding: 12px 20px;
      font-family: var(--secondary-font);
      font-weight: var(--font-weight-medium);
      font-size: 14px;
      line-height: 17px;
      color: var(--main-text-color);
      cursor: pointer;
      transition: var(--main-transition);
      border-bottom: 1px solid var(--grayColor200);

      &:first-child {
        border-radius: 18px 18px 0 0;
      }

      &:last-child {
        border-bottom: none;
        border-radius: 0 0 18px 18px;
      }

      &:only-child {
        border-radius: 18px;
      }

      &:hover {
        background-color: var(--primary-color);
        color: var(--main-white-color);
      }

      &--selected {
        background-color: var(--light-primary-color);
        color: var(--primary-color);
        font-weight: var(--font-weight-bold);
      }
    }
  }
</style>
