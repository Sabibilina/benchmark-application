FROM grafana/k6:0.51.0

WORKDIR /scripts

# Copy all scripts
COPY scripts/ .

# Create the data directory where seed.js writes seed.json
RUN mkdir -p /scripts/data

# Default: run main.js. Override CMD in docker-compose for seed or lag-check.
ENTRYPOINT ["k6", "run"]
CMD ["/scripts/main.js"]
