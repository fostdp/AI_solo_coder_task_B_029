FROM timescale/timescaledb:2.13.0-pg14

COPY src/main/resources/db/init.sql /docker-entrypoint-initdb.d/01-init.sql
