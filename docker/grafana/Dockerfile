# Custom Grafana image for nginxWebUI stack
# Bakes datasources + dashboards into image so deployments only need
# docker-compose.yml + .env (no local config files required)

FROM grafana/grafana:11.6.0

COPY datasources.yml      /etc/grafana/provisioning/datasources/loki.yml
COPY dashboards.yml       /etc/grafana/provisioning/dashboards/default.yml
COPY nginx-dashboard.json /var/lib/grafana/dashboards/nginx-monitor.json
