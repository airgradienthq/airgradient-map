<template>
  <v-select
    :items="options"
    :item-title="'label'"
    :item-value="'value'"
    :model-value="selectedValue"
    :disabled="disabled"
    class="ag-dropdown-control"
    variant="outlined"
    :density="props.size === DropdownSize.NORMAL ? 'comfortable' : 'compact'"
    @update:modelValue="handleChange"
  />
</template>

<script setup lang="ts">
  import { PropType, ref, watch } from 'vue';
  import { DropdownOption, DropdownSize } from '~/types';

  const props = defineProps({
    /**
     * Array of options for the dropdown.
     * Each option should be of type DropdownOption.
     */
    options: {
      type: Array as PropType<Array<DropdownOption>>,
      required: true
    },
    /**
     * The selected value.
     */
    selectedValue: {
      type: [String, Number],
      default: ''
    },
    /**
     * Size of the dropdown.
     * @type DropdownSize
     * @default DropdownSize.NORMAL
     */
    size: {
      type: String as PropType<DropdownSize>,
      default: DropdownSize.SMALL
    },
    /**
     * Whether the dropdown is disabled.
     * @type {boolean}
     * @default false
     */
    disabled: {
      type: Boolean,
      default: false
    }
  });

  const emit = defineEmits(['update:modelValue', 'change']);

  const currentValue = ref(props.selectedValue);

  watch(
    () => props.selectedValue,
    newVal => {
      currentValue.value = newVal;
    }
  );

  /**
   * Handles the change event for the dropdown.
   * Emits the new value.
   * @param {Event} event - The change event.
   */
  const handleChange = (value: string | number) => {
    emit('update:modelValue', value);
    emit('change', value);
  };
</script>

<style lang="scss">
  .ag-dropdown-control {
    transition: var(--main-transition);
    color: var(--primary-color);
    font-family: var(--secondary-font);
    font-weight: 700;

    .v-field {
      transition: var(--main-transition);
      background-color: var(--main-white-color) !important;
    }

    .v-field__outline__end {
      border: solid 2px var(--primary-color);
      border-left: none;
      opacity: 1;
    }

    .v-field__outline__start {
      border: solid 2px var(--primary-color);
      border-right: none;
      opacity: 1;
    }

    .v-icon {
      opacity: 1;
    }

    &:hover {
      color: var(--main-white-color);

      .v-field {
        background-color: var(--primary-color) !important;
      }
    }

    .v-field--disabled {
      opacity: 1;
      color: var(--main-disabled-color);

      .v-field__outline__end {
        border-color: var(--main-disabled-color);
      }

      .v-field__outline__start {
        border-color: var(--main-disabled-color);
      }
    }
  }
</style>
