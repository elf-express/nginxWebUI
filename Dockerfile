FROM alpine:3.24
ENV LANG=zh_TW.UTF-8 \
    TZ=Asia/Taipei \
    JVM_XMX=256m \
    PS1="\u@\h:\w \$ "
# 精簡模組集：保留 NDK/Lua/njs/GeoIP2/壓縮/安全/觀測等，排除未維護或高風險模組
# （upstream_fair、legacy geoip、perl、upload*、zip、untar、slowfs、echo、dav、fancyindex、xslt、shibboleth、log_zmq、accounting、redis2）
RUN    apk add --update --no-cache \
       nginx \
       nginx-mod-devel-kit \
       nginx-mod-stream \
       nginx-mod-stream-geoip2 \
       nginx-mod-stream-js \
       nginx-mod-stream-keyval \
       nginx-mod-mail \
       nginx-mod-rtmp \
       nginx-mod-dynamic-upstream \
       nginx-mod-dynamic-healthcheck \
       nginx-mod-http-headers-more \
       nginx-mod-http-js \
       nginx-mod-http-keyval \
       nginx-mod-http-lua \
       nginx-mod-http-lua-upstream \
       nginx-mod-http-brotli \
       nginx-mod-http-zstd \
       nginx-mod-http-geoip2 \
       nginx-mod-http-cache-purge \
       nginx-mod-http-set-misc \
       nginx-mod-http-array-var \
       nginx-mod-http-encrypted-session \
       nginx-mod-http-cookie-flag \
       nginx-mod-http-image-filter \
       nginx-mod-http-auth-jwt \
       nginx-mod-http-naxsi \
       nginx-mod-http-nchan \
       nginx-mod-http-vts \
       nginx-mod-http-vod \
       nginx-mod-http-acme \
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
# Bake GeoLite2 MMDB at build time so geoip works offline out-of-box
# (entrypoint + cron still refresh when online; 7-day freshness skip)
RUN curl -fL --retry 3 -o /etc/nginx/geoip/GeoLite2-Country.mmdb https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-Country.mmdb \
    && curl -fL --retry 3 -o /etc/nginx/geoip/GeoLite2-City.mmdb    https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-City.mmdb \
    && curl -fL --retry 3 -o /etc/nginx/geoip/GeoLite2-ASN.mmdb     https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-ASN.mmdb
VOLUME ["/home/nginxWebUI"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -sf http://localhost:8080 || exit 1
ENTRYPOINT ["tini", "entrypoint.sh"]
