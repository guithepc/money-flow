FROM node:26-alpine AS build
WORKDIR /app
COPY package.json package-lock.json .
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ARG VITE_SUPPORT_EMAIL
ENV VITE_SUPPORT_EMAIL=$VITE_SUPPORT_EMAIL
RUN npm run build
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
