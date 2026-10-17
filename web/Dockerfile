FROM node:24-alpine@sha256:44fce696f38ba8c987c4db89954051000bbd05ffd8d8659ba15ed4a8602e937f AS build
WORKDIR /src
# npm workspaces: every manifest first for layer caching — add each new MFE here.
COPY package.json package-lock.json ./
COPY web/package.json web/package.json
COPY mfe/account/package.json mfe/account/package.json
COPY packages/mfe-kit/package.json packages/mfe-kit/package.json
COPY packages/ui/package.json packages/ui/package.json
RUN npm ci
COPY web/ web/
COPY mfe/ mfe/
COPY packages/ packages/
COPY scripts/ scripts/
RUN npm run build:web

FROM nginx:1.27-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10 AS runtime
COPY web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/web/dist /usr/share/nginx/html/app
EXPOSE 80
