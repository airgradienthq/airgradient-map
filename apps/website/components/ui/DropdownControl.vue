<template>
  <v-select
    :model-value="selectedValue"
    :items="options"
    :placeholder="placeholder"
    :disabled="disabled"
    class="ag-dropdown-control"
    :class="{
      'ag-dropdown-control--small': size === 'small'
    }"
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
  >
    <template #append-inner>
      <svg width="20" height="20" viewBox="0 0 24 24" class="ag-dropdown-control__icon">
        <path d="M8 5V19L19 12L8 5Z" fill="currentColor" />
      </svg>
    </template>
  </v-select>
</template>

<script setup lang="ts">
  import { PropType } from 'vue';
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

  const handleChange = (value: string | number) => {
    emit('update:modelValue', value);
    emit('change', value);
  };
</script>

<style lang="scss">
.ag-dropdown-control {
  position: relative;
  width: 100%;
  font-family: var(--secondary-font);

  .v-field {
    width: 100% !important;
    display: flex !important;
    align-items: center !important;
    justify-content: space-between !important;
    padding: 0 20px !important;
    background-color: var(--main-white-color) !important;
    border: 2px solid var(--grayColor400) !important;
    border-radius: 100px !important;
    font-family: var(--secondary-font) !important;
    font-weight: var(--font-weight-medium) !important;
    font-size: 14px !important;
    line-height: 28px !important;
    color: var(--main-text-color) !important;
    cursor: pointer !important;
    transition: var(--main-transition) !important;
    height: 38px !important;
    min-height: 38px !important;
    max-height: 38px !important;
    box-shadow: none !important;
  }

  .v-field:hover:not(.v-field--disabled) {
    background-color: var(--primary-color) !important;
    color: var(--main-white-color) !important;
    border-color: var(--primary-color) !important;
  }

  .v-field--focused {
    background-color: var(--primary-color) !important;
    color: var(--main-white-color) !important;
    border-color: var(--primary-color) !important;
  }

  .v-field--disabled {
    cursor: not-allowed !important;
    color: var(--main-disabled-color) !important;
    border-color: var(--main-disabled-color) !important;
    background-color: var(--main-white-color) !important;
  }

  .v-field--disabled:hover {
    background-color: var(--main-white-color) !important;
    color: var(--main-disabled-color) !important;
    border-color: var(--main-disabled-color) !important;
  }

  .v-field__input {
    flex: 1 !important;
    font-family: var(--secondary-font) !important;
    font-weight: var(--font-weight-medium) !important;
    font-size: 14px !important;
    line-height: 17px !important;
    color: inherit !important;
    padding: 0 !important;
    background: transparent !important;
    border: none !important;
    outline: none !important;
    cursor: pointer !important;
  }

  .v-field:hover:not(.v-field--disabled) .v-field__input,
  .v-field--focused .v-field__input {
    color: var(--main-white-color) !important;
  }

  .v-field--disabled .v-field__input {
    color: var(--main-disabled-color) !important;
  }

  .v-field__outline,
  .v-field__outline__start,
  .v-field__outline__notch,
  .v-field__outline__end {
    display: none !important;
    opacity: 0 !important;
    border: none !important;
  }

  &__icon {
    transform: rotate(90deg) !important;
    transition: var(--main-transition) !important;
    flex-shrink: 0 !important;
    width: 20px !important;
    height: 20px !important;
    color: currentColor !important;
  }

  .v-field--focused .ag-dropdown-control__icon {
    transform: rotate(270deg) !important;
  }

  .v-field__append-inner {
    padding: 0 !important;
    align-self: center !important;
  }

  .v-select__menu-icon {
    display: none !important;
  }

  &--small {
    .v-field {
      padding: 0 16px !important;
      font-size: 14px !important;
      line-height: 24px !important;
      height: 36px !important;
      min-height: 36px !important;
      max-height: 36px !important;
    }
  }
}

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
    font-family: var(--secondary-font) !important;
    font-weight: var(--font-weight-medium) !important;
    font-size: 14px !important;
    line-height: 17px !important;
    color: var(--main-text-color) !important;
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

    .v-list-item__content,
    .v-list-item-title {
      font-size: inherit !important;
      font-weight: inherit !important;
      line-height: inherit !important;
      color: inherit !important;
      padding: 0 !important;
    }
  }
}
</style>