# Custom CrowdSec image for nginxWebUI stack
# Bakes acquisition / profiles / notification config and the hub content (log parsers + AppSec WAF)
# into the image, so the container starts without reaching the CrowdSec hub.
# Runtime secrets (BOUNCER_KEY, ABUSEIPDB_API_KEY) still come from .env
FROM crowdsecurity/crowdsec:v1.8.1

# The official start script runs `cscli hub upgrade` on every start when the data dir is a volume
# (compose always mounts one); this switch turns that off. Hub content only changes by rebuilding the image.
ENV NO_HUB_UPGRADE=true

# The stock image keeps its config in /staging/etc/crowdsec and copies it to /etc/crowdsec on first start.
# Build the final /etc/crowdsec here instead and install the hub content with it.
# AppSec rule sources are only virtual patching (appsec-virtual-patching) and the OWASP CRS (appsec-crs).
RUN set -eux; \
    mkdir -p /etc/crowdsec; \
    cp -a /staging/etc/crowdsec/. /etc/crowdsec/; \
    cscli hub update; \
    cscli parsers install crowdsecurity/docker-logs crowdsecurity/cri-logs; \
    cscli collections install \
        crowdsecurity/nginx \
        crowdsecurity/http-cve \
        crowdsecurity/base-http-scenarios \
        crowdsecurity/appsec-virtual-patching \
        crowdsecurity/appsec-crs

# Data files the hub items download (CRS rule data etc.) land in /var/lib/crowdsec/data, which compose hides
# behind a volume. The start script symlinks everything in /staging/var/lib/crowdsec/data into that volume,
# so move them there.
RUN find /var/lib/crowdsec/data -mindepth 1 -maxdepth 1 ! -name 'crowdsec.db*' \
        -exec mv -n {} /staging/var/lib/crowdsec/data/ \;

# The start script also runs `cscli parsers install` for docker-logs / cri-logs on every start, and every
# `cscli ... install` asks version.crowdsec.net for the latest version first (fails without network, and
# `set -e` then stops the container). Both parsers are installed above, so those two lines are removed.
RUN sed -i '/cscli_if_clean parsers install crowdsecurity\/\(docker\|cri\)-logs/d' /docker_start.sh \
    && ! grep -q 'crowdsecurity/\(docker\|cri\)-logs' /docker_start.sh

COPY acquis.yml     /etc/crowdsec/acquis.yaml
COPY appsec.yaml    /etc/crowdsec/acquis.d/appsec.yaml
COPY profiles.yaml  /etc/crowdsec/profiles.yaml
COPY abuseipdb.yaml /etc/crowdsec/notifications/abuseipdb.yaml

# Start wrapper: hands the nginxWebUI machine password (secrets file or env) to the official start script,
# which registers the machine with `cscli machines add --force` on every start.
COPY nwu-start.sh /nwu-start.sh
RUN chmod 755 /nwu-start.sh
ENTRYPOINT ["/bin/bash", "/nwu-start.sh"]
