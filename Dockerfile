FROM alpine:3.22
ENV LANG=zh_TW.UTF-8 \
    TZ=Asia/Taipei \
    JVM_XMX=256m \
    PS1="\u@\h:\w \$ "
# RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories \
RUN    apk add --update --no-cache \
       nginx \
	   nginx-mod-stream \
	   nginx-mod-stream-geoip \
	   nginx-mod-stream-geoip2 \
	   nginx-mod-stream-js \
	   nginx-mod-stream-keyval \
	   nginx-mod-http-headers-more \
	   nginx-mod-http-js \
	   nginx-mod-http-keyval \
	   nginx-mod-http-lua \
	   nginx-mod-http-brotli \
	   nginx-mod-rtmp \
	   nginx-mod-mail \
	   nginx-mod-http-geoip \
	   nginx-mod-http-geoip2 \
	   nginx-mod-http-zip \
	   nginx-mod-http-zstd \
	   nginx-mod-http-perl \
	   nginx-mod-http-upload \
	   nginx-mod-http-upload-progress \
	   nginx-mod-http-upstream-fair \
	   nginx-mod-http-upstream-jdomain \
	   nginx-mod-http-echo \
	   nginx-mod-http-cache-purge \
	   nginx-mod-dynamic-upstream \
	   nginx-mod-dynamic-healthcheck \
       openjdk17-jre \
       net-tools \
       curl \
       wget \
       ttf-dejavu \
       fontconfig \
       tzdata \
       logrotate \
       tini \
       acme.sh \
    && fc-cache -f -v \
    && ln -sf /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo "${TZ}" > /etc/timezone \
    && rm -rf /var/cache/apk/* /tmp/*
COPY target/nginxWebUI-*.jar /home/nginxWebUI.jar
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
COPY scripts/update-geoip-cf.sh /usr/local/bin/update-geoip-cf.sh
RUN chmod +x /usr/local/bin/entrypoint.sh /usr/local/bin/update-geoip-cf.sh \
    && mkdir -p /etc/nginx/geoip /etc/nginx/conf.d \
    && echo "0 3 * * 3,6 /usr/local/bin/update-geoip-cf.sh >> /var/log/update-geoip-cf.log 2>&1" > /etc/crontabs/root
VOLUME ["/home/nginxWebUI"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -sf http://localhost:8080 || exit 1
ENTRYPOINT ["tini", "entrypoint.sh"]
