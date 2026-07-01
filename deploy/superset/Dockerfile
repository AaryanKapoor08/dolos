# Superset with the PostgreSQL driver (Phase 6C).
#
# The official apache/superset image ships no DB drivers, but Dolos uses Postgres for BOTH Superset's
# own metadata store AND the analytics source (the read models), so psycopg2 is required. This is the
# documented way to add drivers to Superset — a thin layer over the pinned base image.
FROM apache/superset:4.1.1
USER root
RUN pip install --no-cache-dir psycopg2-binary==2.9.10
USER superset
