FROM grafana/k6:0.51.0
WORKDIR /scripts
COPY scripts/ .
ENTRYPOINT ["k6", "run", "/scripts/main.js"]
