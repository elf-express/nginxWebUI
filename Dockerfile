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
       lua-resty-http \
       lua5.1-cjson \
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
# resty.string (needed by lua-resty-http) and resty.sha / resty.sha256 (nwu challenge, FFI to libcrypto). The Alpine
# lua-resty-string package conflicts with nginx (it pulls in openresty), so only these pure-Lua files are extracted.
RUN mkdir -p /tmp/lrs/x /usr/share/lua/common/resty \
    && apk fetch -q -o /tmp/lrs lua-resty-string \
    && tar xzf /tmp/lrs/lua-resty-string-*.apk -C /tmp/lrs/x usr/share/lua/common/resty/string.lua \
        usr/share/lua/common/resty/sha.lua usr/share/lua/common/resty/sha256.lua \
    && cp /tmp/lrs/x/usr/share/lua/common/resty/string.lua /tmp/lrs/x/usr/share/lua/common/resty/sha.lua \
        /tmp/lrs/x/usr/share/lua/common/resty/sha256.lua /usr/share/lua/common/resty/ \
    && rm -rf /tmp/lrs
# CrowdSec official lua-cs-bouncer (from the cs-nginx-bouncer release tarball, pinned + sha256 verified).
# Libraries go to /usr/local/lib/crowdsec/lua/, ban/captcha templates to /var/lib/crowdsec/lua/templates/.
ARG CS_NGINX_BOUNCER_VERSION=v1.2.3
ARG CS_NGINX_BOUNCER_SHA256=8cb0c176f01bda3a5fc5493d20bcd7261630c7dfbd60c00b2bff8eefc8fe84d5
RUN curl -fL --retry 3 -o /tmp/cs-nginx-bouncer.tgz \
        "https://github.com/crowdsecurity/cs-nginx-bouncer/releases/download/${CS_NGINX_BOUNCER_VERSION}/crowdsec-nginx-bouncer.tgz" \
    && echo "${CS_NGINX_BOUNCER_SHA256}  /tmp/cs-nginx-bouncer.tgz" | sha256sum -c - \
    && mkdir -p /tmp/cs-nginx-bouncer /usr/local/lib/crowdsec/lua /var/lib/crowdsec/lua/templates \
    && tar xzf /tmp/cs-nginx-bouncer.tgz -C /tmp/cs-nginx-bouncer --strip-components=1 \
    && cp -r /tmp/cs-nginx-bouncer/lua-mod/lib/. /usr/local/lib/crowdsec/lua/ \
    && cp -r /tmp/cs-nginx-bouncer/lua-mod/templates/. /var/lib/crowdsec/lua/templates/ \
    && rm -rf /tmp/cs-nginx-bouncer /tmp/cs-nginx-bouncer.tgz
# Bouncer patch: in stream mode the expiry of a captcha decision must not delete a later ban of the same IP from the
# bouncer cache (stream.lua deletes by IP only). The script checks the bouncer version and the exact source lines
# and fails the build otherwise, so upgrading CS_NGINX_BOUNCER_VERSION means re-checking the patch.
COPY docker/lua/patches/cs-stream-delete-by-type.sh /tmp/nwu-patches/
RUN sh /tmp/nwu-patches/cs-stream-delete-by-type.sh "${CS_NGINX_BOUNCER_VERSION}" \
        /usr/local/lib/crowdsec/lua/plugins/crowdsec/stream.lua \
    && rm -rf /tmp/nwu-patches
# ALTCHA widget (MIT) for the self-hosted challenge page, pinned + sha256 verified. Lua serves it on the reserved path
# /.well-known/nwu-challenge/altcha.js, so the challenge page loads nothing from outside.
ARG ALTCHA_VERSION=2.3.0
ARG ALTCHA_SHA256=559c584cd1fa932767c51497b56b2955282a7cbc08c1a3bb943665e572b45d3f
RUN curl -fL --retry 3 -o /tmp/altcha.tgz "https://registry.npmjs.org/altcha/-/altcha-${ALTCHA_VERSION}.tgz" \
    && echo "${ALTCHA_SHA256}  /tmp/altcha.tgz" | sha256sum -c - \
    && mkdir -p /tmp/altcha /usr/local/lib/nginxwebui/html \
    && tar xzf /tmp/altcha.tgz -C /tmp/altcha package/dist/altcha.umd.cjs package/LICENSE.txt \
    && cp /tmp/altcha/package/dist/altcha.umd.cjs /usr/local/lib/nginxwebui/html/altcha.js \
    && cp /tmp/altcha/package/LICENSE.txt /usr/local/lib/nginxwebui/html/altcha.LICENSE.txt \
    && rm -rf /tmp/altcha /tmp/altcha.tgz
# nwu wrapper (nwu_crowdsec / nwu_crowdsec_policy), nwu challenge, Lua path snippet and 503 page
COPY docker/lua/nwu_crowdsec.lua docker/lua/nwu_crowdsec_policy.lua docker/lua/nwu_challenge.lua \
     docker/lua/nwu_challenge_policy.lua /usr/local/lib/nginxwebui/lua/
COPY docker/lua/crowdsec_lua_paths.conf /etc/nginx/nwu/crowdsec_lua_paths.conf
COPY docker/lua/protection-paused.html /usr/local/lib/nginxwebui/html/protection-paused.html
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
