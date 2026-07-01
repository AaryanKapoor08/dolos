# Superset configuration (Phase 6C) — local BI over the Dolos read models.
#
# Mounted onto the image's PYTHONPATH (/app/pythonpath/superset_config.py). Keeps Superset's own
# metadata in a dedicated `superset` database on the shared Postgres server, and relaxes CSRF/Talisman
# so the provisioning script can drive the REST API cleanly. Local-dev only — never production.
import os

# Superset's METADATA store (its own dashboards/charts/users), separate from the analytics data.
SQLALCHEMY_DATABASE_URI = os.environ.get(
    "SUPERSET_METADATA_URI", "postgresql+psycopg2://dolos:dolos@postgres:5432/superset"
)

SECRET_KEY = os.environ.get("SUPERSET_SECRET_KEY", "dolos-superset-dev-secret-change-me-please-32b")

SQLALCHEMY_TRACK_MODIFICATIONS = False

# Simplify local API automation + embedding. (Do not do this in production.)
WTF_CSRF_ENABLED = False
TALISMAN_ENABLED = False

FEATURE_FLAGS = {
    "EMBEDDED_SUPERSET": True,
    "DASHBOARD_RBAC": False,
}

# In-process cache is fine for a single-node local demo (no Redis/Celery).
CACHE_CONFIG = {"CACHE_TYPE": "SimpleCache", "CACHE_DEFAULT_TIMEOUT": 300}
