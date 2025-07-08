<script setup>
  useHead({
    title: "404 Page not found"
  })
</script>

<template>
  <NuxtLayout>
    <!-- original -->
    <div class="container">
      <div class="row">
        <div class="col-md-6 text-center mb-5 mb-md-0">
          <img 
            src="https://www.airgradient.com/images/404.jpg" 
            class="img-fluid"
            alt="404 Error"
          />
        </div>
        <div class="col-md-6 align-self-center text-center text-md-start">
          <h1 class="mb-4">You ran out of air.</h1>
          <p class="mb-4">The page you're looking for is beyond our reach…</p>
          <NuxtLink to="/" class="btn-large button-blue">
            Go to Homepage
          </NuxtLink>
        </div>
      </div>
    </div>

    <!-- centered + img-focused -->
    <!-- <div class="vh-100 d-flex align-items-center justify-content-center text-center">
      <div>
        <h1 class="display-2 font-weight-bold mb-0">
          You ran out of air.
        </h1>
        <div>
          <img
            src="https://www.airgradient.com/images/404.jpg"
            class="img-fluid"
            style="max-width: 400px; height: auto;"
          />
        </div>
        <p class="fs-2">We can help with that.</p>
        <p>But first, let's get out of this page.</p>
        <NuxtLink to="/" class="btn-large button-blue">
          Go to Homepage
        </NuxtLink>
      </div>
    </div> -->

    <!-- staggered -->
    <!-- <div class="container justify-content-center">
      <div class="row">
        <h1 class="col-sm text-start text-md-end mt-10">You ran out of air.</h1>
        <img
          src="https://www.airgradient.com/images/404.jpg"
          class="col-sm img-responsive"
        />
        <div class="col-sm"></div>
      </div>
      <div class="text-end justify-content-center">
        <p class="">The page you're looking for is beyond our reach...</p>
        <NuxtLink to="/" class="btn-large button-blue">
          Go to Homepage
        </NuxtLink>
      </div>
    </div> -->

  </NuxtLayout>
</template>
