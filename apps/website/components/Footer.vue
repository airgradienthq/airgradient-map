<!-- Internal company Footer component -->

<template>
  <footer id="main-footer">
    <div class="footer-inner bg-blue">
      <div class="footer-content">
        <div class="d-flex flex-column flex-lg-row justify-content-between">
          <div class="d-flex flex-column info-container">
            <div class="logo-container">
              <a target="_blank" href="https://www.airgradient.com/">
                <img alt="AirGradient Logo" src="assets/images/logos/logo_white.svg" />
              </a>
            </div>
            <h2 class="text-white">Open Data <br>Real Impact <br>No Secrets.</h2>
            <span class="footer-buttons d-flex flex-wrap">
              <a
                target="_blank"
                href="https://www.airgradient.com/onboarding/welcome/"
                class="btn-small button-orange"
              >
                Get Started with AirGradient
              </a>
            </span>
          </div>
          <div class="footer-links-container">
            <div class="footer-links-inner d-flex flex-column flex-md-row justify-content-between">
              <template v-for="(item, index) in FOOTER_LINKS_CONFIG" :key="index">
                <ul class="footer-links">
                  <li v-for="(link, index) in item.links" :key="index">
                    <a :target="link.openBlank ? '_blank' : ''" :href="link.path">
                      {{ link.label }}
                    </a>
                  </li>
                </ul>
                <hr v-if="index !== FOOTER_LINKS_CONFIG.length - 1" class="d-block d-md-none" />
              </template>
            </div>
            <hr class="d-none d-lg-block" />
            <div class="social-links">
              <h5 class="text-white">Connect with us</h5>
              <span class="d-flex flex-row social-links-inner">
                <a
                  href="https://www.linkedin.com/company/airgradient/"
                  target="_blank"
                  class="button-white icon-btn"
                >
                  <img src="/assets/images/icons/linkedin.svg" alt="LinkedIn Icon" />
                </a>
                <a
                  href="https://www.youtube.com/@AirGradient"
                  target="_blank"
                  class="button-white icon-btn"
                >
                  <img src="/assets/images/icons/youtube.svg" alt="YouTube Icon" />
                </a>
                <a
                  href="https://www.airgradient.com/newsletter-signup"
                  target="_blank"
                  class="btn-small button-white"
                  >Join Newsletter</a
                >
                <a
                  target="_blank"
                  href="https://www.airgradient.com/blog/living-and-working-in-ukraine-during-war/"
                  class="mt-1 ml-4"
                >
                  <div style="height: 15px; width: 50px; background-color: #0057b7"></div>
                  <div style="height: 15px; width: 50px; background-color: #ffdd00"></div>
                </a>
              </span>
            </div>
          </div>
        </div>
        <div class="copyright d-flex flex-wrap justify-content-between" style="margin-top: 20px">
          <span class="copyright-text text-left mr-5">© AirGradient Co. Ltd.</span>
          <span class="copyright-text text-left"
            >181 M.10 Baan Nam Long Soi 2 Chiang Mai 50180, Thailand</span
          >
        </div>
      </div>
    </div>
    <div class="bg-darkblue">
      <div class="container d-flex flex-row justify-content-center align-items-center">
        <a target="_blank" href="https://www.airgradient.com/blog/1-percent-for-the-planet/">
          <img
            class="mr-4"
            style="height: 60px"
            src="/assets/images/logos/1percent.png"
            alt="1 Percent logo"
          />
        </a>
        <p class="text-white m-0">
          We’ve pledged 1% of sales to the preservation and restoration of the natural environment.
        </p>
      </div>
    </div>
  </footer>
</template>
<script setup lang="ts">
  import { FOOTER_LINKS_CONFIG } from '~/constants/shared/footer-links-config';
</script>
<style lang="scss" scoped>
  footer {
    width: 100%;
    overflow: hidden;

    .bg-darkblue {
      padding: 30px;
    }

    .footer-content {
      margin: 0 138px;
    }

    .footer-inner {
      padding: 103px 0 20px !important;
    }

    .flag-ua {
      margin-top: 60px;
    }

    & .info-container {
      width: 50%;
      margin-right: 100px;
      max-width: 450px;
    }

    .footer-buttons {
      gap: 20px;
      margin-bottom: 60px;

      a {
        width: fit-content;
        height: fit-content;
      }
    }

    .logo-container {
      margin-bottom: 60px;

      & img {
        width: 200px;

        height: auto;
      }
    }

    .copyright {
      p {
        color: white;
        font-size: 14px;
      }
    }

    hr {
      border-top: 1px solid #d4ecff !important;
      opacity: 1;
      width: 100%;
      margin: 39px 0;
    }

    h2 {
      margin-bottom: 50px;
    }

    .social-links {
      .social-links-inner {
        flex-wrap: wrap;
        row-gap: 16px;
      }

      h5 {
        margin-bottom: 20px;
      }

      .btn-small {
        line-height: 30px;
      }

      .icon-btn {
        border-radius: 50%;
        padding: 7px;
        margin-right: 16px;

        img {
          width: 26px;
          height: 26px;
          transition: all 150ms ease-in-out;
        }

        &:hover,
        &:focus {
          img {
            filter: brightness(0.8);
          }
        }
      }
    }

    .footer-links-container {
      width: 50%;
    }

    .footer-links-inner {
      gap: 15px;
    }

    & .footer-links {
      padding: 0;
      margin: 0;
      list-style-type: none;

      li {
        &:not(:last-child) {
          margin-bottom: 15px;
        }

        a {
          line-height: 27px;
          text-decoration: none;
          color: white;

          &:hover,
          &:focus {
            text-decoration: underline;
          }
        }
      }
    }
  }

  @include desktop-lg {
    .footer-content {
      margin: 0 30px !important;
    }
  }

  @include desktop {
    footer {
      .footer-inner {
        padding: 60px 0 !important;
      }

      & .info-container {
        margin-right: 0;
        width: 100%;
      }

      .footer-links-container {
        width: 100%;
      }

      .footer-links-inner {
        margin: 60px 0;
      }

      .footer-buttons {
        margin-bottom: 0;
        gap: 20px !important;
      }

      .social-links {
        margin-bottom: 50px;

        h5 {
          margin-bottom: 15px;
        }
      }

      hr {
        margin: 5px 0;
      }

      .logo-container {
        margin-bottom: 50px;

        & img {
          width: 145px;
          height: auto;
        }
      }
    }
  }
</style>
