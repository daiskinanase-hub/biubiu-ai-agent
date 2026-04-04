# 阶段一：编译
FROM node:20-alpine AS build
WORKDIR /app
COPY . .
# 如果本地网络慢，可以使用镜像源：RUN npm install --registry=https://registry.npmmirror.com
RUN npm install
RUN npm run build

# 阶段二：运行
FROM nginx:alpine
# 拷贝编译产物
COPY --from=build /app/dist /usr/share/nginx/html
# 拷贝上面的自定义 nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]