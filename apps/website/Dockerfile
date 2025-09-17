FROM node:22-alpine AS build
WORKDIR /app

# Accept APP_ENV as build argument
ARG APP_ENV=development
ENV APP_ENV=$APP_ENV

COPY package.json package-lock.json .npmrc ./

RUN npm install --ignore-scripts \
  && npm install rollup \
  && npm rebuild \
  && npm run prepare

COPY . .

RUN npm run build

FROM node:22-alpine
WORKDIR /app

COPY --from=build /app/.output/ ./

ARG APP_ENV=development
ENV APP_ENV=$APP_ENV
ENV HOST=0.0.0.0
ENV PORT=80

EXPOSE 80

CMD ["node", "/app/server/index.mjs"]
