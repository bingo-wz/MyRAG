FROM node:22-alpine AS builder
WORKDIR /workspace
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/dist /usr/share/nginx/html
COPY docker-entrypoint.d/40-myrag-config.sh /docker-entrypoint.d/40-myrag-config.sh
RUN chmod +x /docker-entrypoint.d/40-myrag-config.sh
EXPOSE 80
