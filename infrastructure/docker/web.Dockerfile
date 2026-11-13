# syntax=docker/dockerfile:1
# Web image: install workspace deps, build via turbo, prune dev deps, serve with Next.js.
# Build context must be the repository root.

FROM node:20-alpine
ENV NODE_ENV=production
WORKDIR /app

COPY package.json package-lock.json turbo.json ./
COPY apps ./apps
COPY packages ./packages

RUN npm ci && npx turbo build --filter=@squamed/web && npm prune --omit=dev

EXPOSE 3000
CMD ["npm", "run", "start", "-w", "@squamed/web"]