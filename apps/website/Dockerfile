FROM node:22-alpine AS build
WORKDIR /app

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

ENV NODE_ENV=production
ENV HOST=0.0.0.0
ENV PORT=80

EXPOSE 80

CMD ["node", "/app/server/index.mjs"]