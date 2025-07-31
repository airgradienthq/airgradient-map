<!-- Internal company Header component -->

<template>
  <header id="main-header" class="bg-white">
    <div class="container header-container">
      <nav class="navbar navbar-expand-lg navbar-light">
        <a target="_blank" class="navbar-brand" href="https://www.airgradient.com">
          <img class="logo-image" alt="AirGradient Logo" src="assets/images/logos/logo_blue.svg" />
        </a>
        <button
          class="navbar-toggler border-0"
          type="button"
          data-toggle="collapse"
          data-target="#navigation"
          aria-controls="navigation"
          aria-expanded="false"
          aria-label="Toggle navigation"
          @click="toggleNavbar()"
        >
          <span class="navbar-toggler-icon"></span>
        </button>
        <div
          id="navigation"
          class="collapse navbar-collapse text-center"
          :class="{ show: isNavbarVisible }"
        >
          <ul class="navbar-nav ml-auto">
            <li
              v-for="(link, index) in HEADER_LINKS_CONFIG"
              :key="index"
              class="nav-item"
              :class="{ dropdown: link.children }"
            >
              <a
                class="nav-link"
                :class="{
                  'dropdown-toggle': link.children,
                  'active-dropdown': activeDropdown === index
                }"
                :href="link.children ? '#' : link.path"
                :target="link.openBlank ? '_blank' : ''"
                @click="
                  () => {
                    link.children ? toggleDropdown(index) : null;
                  }
                "
              >
                {{ link.label }}
                <span v-if="link.children" class="dropdown-arrow"></span>
              </a>
              <div class="dropdown-shadow">
                <div
                  v-if="link.children"
                  class="dropdown-menu"
                  :class="{ show: activeDropdown === index }"
                >
                  <a
                    v-for="(child, childIndex) in link.children"
                    :key="childIndex"
                    class="dropdown-item"
                    :href="child.path"
                    :target="child.openBlank ? '_blank' : ''"
                  >
                    {{ child.label }}
                  </a>
                </div>
              </div>
            </li>
          </ul>
        </div>
      </nav>
    </div>
  </header>
</template>
<script setup lang="ts">
  import { HEADER_LINKS_CONFIG } from '~/constants/shared/header-links-config';
  import { ref, onMounted, onBeforeUnmount } from 'vue';

  const isNavbarVisible = ref(false);
  const activeDropdown = ref<number | null>(null);

  const toggleNavbar = () => {
    isNavbarVisible.value = !isNavbarVisible.value;
  };

  const toggleDropdown = (index: number) => {
    activeDropdown.value = activeDropdown.value === index ? null : index;
  };

  const closeDropdown = event => {
    if (!(event.target as HTMLElement).closest('.dropdown') && activeDropdown != null) {
      activeDropdown.value = null;
    }
  };

  onMounted(() => {
    document.addEventListener('click', closeDropdown);
  });

  onBeforeUnmount(() => {
    document.removeEventListener('click', closeDropdown);
  });
</script>
<style>
  .main-content {
    min-height: calc(100svh - 125px);
    background: var(--main-white-color);
  }

  @media (max-width: 991px) {
    .main-content {
      min-height: calc(100svh - 112px);
    }
  }
</style>

<style lang="scss" scoped>
  .native-app.ios header {
    margin-top: 40px;
  }

  select {
    border: 1px solid var(--select-border);
    border-radius: 0.25em;
    padding: 0.25em 0.5em;
    cursor: pointer;
    line-height: 1.5;
    background-color: var(--main-white-color);
    background-image: linear-gradient(to top, #f9f9f9, var(--main-white-color) 33%);
  }

  .active {
    background-color: var(--primary-color);
    border-radius: 0px;
    height: 90px;
    color: var(--main-white-color) !important;
  }

  .active2 {
    background-color: #114b79;
    border-radius: 0px;
    height: 50px;
    color: var(--main-white-color) !important;
  }

  .navbar {
    padding: 0px !important;
    line-height: 100px;
  }

  .navbar2 {
    padding: 0px !important;
    line-height: 70px !important;
  }

  .logo-image {
    width: 160px;
  }

  .join-us-btn {
    position: absolute;
    top: 0;
    right: 0;
    border: none !important;
    margin-top: 12px;
    margin-right: 17px;
    color: var(--primary-color);
    z-index: 999;
    transition: all 200ms ease-in-out;
  }

  .join-us-btn:hover {
    background-color: var(--main-white-color) !important;
    color: var(--primaryColor700) !important;
  }

  .header-container {
    padding-top: 25px;
    padding-bottom: 10px;
    position: relative;
  }

  .navbar-toggler {
    color: #e8a325 !important;
    border-width: 1px !important;

    &:focus {
      box-shadow: 0 0 0 2px !important;
    }
  }

  .dropdown-menu {
    border: none;
    border-radius: 0;
    padding: 0;
    display: block;
    opacity: 0;
    visibility: hidden;
    transform-origin: center top;
    transition: all 0.3s ease-in-out;
    border-top: 3px solid var(--primary-color);
    min-width: 250px;
    width: auto;
    position: absolute;
    z-index: 1001;
    left: 50%;
    transform: translateX(-50%) scaleX(0);
    text-align: center;
  }

  .dropdown-shadow {
    box-shadow: 0 2px 5px rgba(0, 0, 0, 0.2);
    background-color: #f8f9fa;
  }

  .nav-link:hover {
    background-color: rgba(28, 117, 188, 0.1);
    color: var(--primary-color) !important;
  }

  .dropdown-arrow {
    margin-left: 4px;
    display: inline-block;
    transition: transform 0.2s;
  }

  .dropdown.dropdown-true .dropdown-menu {
    box-shadow: 0 2px 5px rgba(0, 0, 0, 0.2) !important;
  }

  .dropdown-item {
    padding: 8px 20px;
    color: #444;
    font-weight: normal;
    line-height: 1.2;
    opacity: 0;
    transform: translateY(-10px);
    transition: all 0.3s ease-in-out;
    border-left: 3px solid transparent;
    white-space: normal;
    min-width: 250px;
    overflow-wrap: break-word;
    word-wrap: break-word;
    width: 100%;
    text-align: center;
  }

  .dropdown-item:nth-child(1) {
    transition-delay: 0.075s;
  }
  .dropdown-item:nth-child(2) {
    transition-delay: 0.15s;
  }
  .dropdown-item:nth-child(3) {
    transition-delay: 0.225s;
  }
  .dropdown-item:nth-child(4) {
    transition-delay: 0.3s;
  }
  .dropdown-item:nth-child(5) {
    transition-delay: 0.375s;
  }
  .dropdown-item:nth-child(6) {
    transition-delay: 0.45s;
  }

  .dropdown-item:hover,
  .dropdown-item:focus {
    background-color: #f8f9fa;
    color: var(--primary-color);
    border-left: 3px solid var(--primary-color);
    font-weight: bold;
  }

  .dropdown-item.active {
    background-color: var(--primaryColor500);
    color: var(--main-white-color);
    border-left: 3px solid #114b79;
    font-weight: bold;
  }

  @media (min-width: 992px) {
    .dropdown:hover .dropdown-menu {
      opacity: 1;
      visibility: visible;
      transform: translateX(-50%) scaleX(1);
      box-shadow: 0 2px 5px rgba(0, 0, 0, 0.2);
    }

    .dropdown:hover .dropdown-item {
      transform: translateX(0);
      opacity: 1;
    }
  }

  @media (max-width: 991px) {
    .header-container {
      padding-top: 30px;
    }

    .logo-image {
      max-width: 125px;
    }

    .nav-link {
      text-align: center;
    }

    .dropdown-menu {
      border: none;
      border-radius: 0;
      padding: 0;
      padding-top: 7px;
      padding-bottom: -7px;
      border-top: 3px solid var(--primary-color);
      transform: none !important;
      transition: none !important;
      opacity: 1 !important;
      display: none;
      position: static;
      width: 100%;
      &.show {
        display: block;
      }
    }

    .active-dropdown {
      background-color: #e9ecef;
    }

    .dropdown-item {
      padding: 10px 15px;
      opacity: 1;
      visibility: visible;
    }
  }
</style>
