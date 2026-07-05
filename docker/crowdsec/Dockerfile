# Custom CrowdSec image for nginxWebUI stack
# Bakes acquisition / profiles / notification config into image
# Runtime secrets (BOUNCER_KEY, ABUSEIPDB_API_KEY) still come from .env
FROM crowdsecurity/crowdsec:v1.7.8

COPY acquis.yml     /etc/crowdsec/acquis.yaml
COPY profiles.yaml  /etc/crowdsec/profiles.yaml
COPY abuseipdb.yaml /etc/crowdsec/notifications/abuseipdb.yaml
