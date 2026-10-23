# Build from the repo root: docker build -f web/Dockerfile .
FROM node:22-alpine AS build
WORKDIR /src
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /src/dist /usr/share/nginx/html
COPY web/nginx.conf /etc/nginx/conf.d/default.conf
# Renders config.js.template -> /usr/share/nginx/html/config.js at container start,
# substituting KEYCLOAK_URL from the environment (see src/lib/auth.ts). This keeps
# the Keycloak URL a runtime setting like every other service's external URLs,
# instead of baked into the JS bundle at image-build time.
COPY web/config.js.template /etc/nginx/templates/config.js.template
ENV NGINX_ENVSUBST_OUTPUT_DIR=/usr/share/nginx/html
ENV KEYCLOAK_URL=http://localhost:8080
EXPOSE 80
