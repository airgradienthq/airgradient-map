<template>
  <div class="test-hover-container">
    <h3>Test Hover Text Reveal Effects</h3>

    <div class="test-item">
      <label>Directive Test (v-slide-reveal):</label>
      <div v-slide-reveal class="test-text">
        This is a very long text that should slide left on hover and then slide back to the
        beginning
      </div>
    </div>

    <div class="test-item">
      <label>Component Test (HoverTextReveal):</label>
      <HoverTextReveal class="test-text">
        This is another very long text that should slide left on hover and then slide back to the
        beginning
      </HoverTextReveal>
    </div>

    <div class="test-item">
      <label>CSS Hover Test:</label>
      <div class="css-hover-test">
        <span>This text uses pure CSS hover effects to slide left and back</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import HoverTextReveal from './HoverTextReveal.vue';
</script>

<style scoped>
  .test-hover-container {
    padding: 20px;
    max-width: 600px;
    margin: 0 auto;
  }

  .test-item {
    margin-bottom: 30px;
  }

  .test-item label {
    display: block;
    font-weight: bold;
    margin-bottom: 10px;
    color: #333;
  }

  .test-text {
    border: 1px solid #ccc;
    padding: 10px;
    background: #f9f9f9;
    border-radius: 4px;
    font-size: 14px;
  }

  .css-hover-test {
    border: 1px solid #ccc;
    padding: 10px;
    background: #f9f9f9;
    border-radius: 4px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    max-width: 300px;
  }

  .css-hover-test span {
    display: inline-block;
    transition: transform 0.3s ease-out;
    transform: translateX(0);
  }

  .css-hover-test:hover span {
    transform: translateX(-60px);
  }
</style>
