# syntax=docker/dockerfile:1

# Dev-server image, not a production build: this project is still under active development, and
# docker-compose.yml bind-mounts the source over this image so edits keep hot-reloading exactly
# like `npm run dev` on the host does -- Docker only replaces "five terminals" with one, it
# doesn't change how the frontend is developed. node_modules is excluded from that bind mount
# (see the compose file's own volumes) because esbuild/rollup ship OS-native binaries: a
# node_modules installed on a Windows host cannot run inside this Linux container.
FROM node:22-alpine
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install

COPY . .

EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
