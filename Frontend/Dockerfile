# syntax=docker/dockerfile:1

# Three stages. The last one is the default build target, so a plain `docker build` or
# `docker compose build frontend` produces the production image: the compiled dashboard served
# by nginx. `--target dev` builds the hot-reloading Vite dev server instead (see the
# `frontend-dev` service in docker-compose.yml).

# ---- dependencies: shared by dev and build ---------------------------------------------------
# `npm install`, not `npm ci`: esbuild/rollup/lightningcss ship OS-native binaries, and a
# package-lock.json written on a Windows host does not always list the Linux ones `npm ci` needs.
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install

# ---- dev: Vite dev server with hot reload ----------------------------------------------------
# docker-compose.yml bind-mounts the source over this image so edits keep hot-reloading;
# node_modules is excluded from that mount (a Windows node_modules cannot run in this Linux
# container).
FROM deps AS dev
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]

# ---- build: compile to static files ----------------------------------------------------------
FROM deps AS build
COPY . .
RUN npm run build

# ---- production: nginx serving the build, proxying the APIs ----------------------------------
FROM nginx:1.28-alpine AS production
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
