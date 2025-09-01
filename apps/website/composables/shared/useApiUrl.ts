import { useRuntimeConfig } from 'nuxt/app';

export const useApiUrl = () => {
  const config: { private: { apiUrl: string }; public: { apiUrl: string } } =
    useRuntimeConfig() as unknown as { private: { apiUrl: string }; public: { apiUrl: string } };

  const getApiUrl = () => {
    if (process.server) {
      return config.private.apiUrl;
    } else {
      return config.public.apiUrl;
    }
  };

  return {
    apiUrl: getApiUrl()
  };
};
