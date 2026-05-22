# Custom Promtail image for nginxWebUI stack
# Bakes scrape config (with country/city/asn captures) into image

FROM grafana/promtail:3.5.0

COPY config.yml /etc/promtail/config.yml
