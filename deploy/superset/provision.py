#!/usr/bin/env python3
"""Provision Superset BI assets over the Dolos read models (Phase 6C).

Idempotent: safe to re-run. Waits for the Superset API, logs in, then creates (if absent):
  * a database connection to the shared `dolos` Postgres (the analytics source);
  * four VIRTUAL (SQL) datasets over the live read models;
  * four table charts; and
  * one "Dolos — Financial Crime Overview" dashboard tying them together.

Runs inside the Superset image (which ships `requests`). Talks to the API by service DNS name.
"""
import json
import os
import sys
import time

import requests

BASE = os.environ.get("SUPERSET_URL", "http://superset:8088")
USER = os.environ.get("SUPERSET_ADMIN", "admin")
PW = os.environ.get("SUPERSET_ADMIN_PASSWORD", "admin")
# The analytics source: the shared dolos database holding every service's read model.
ANALYTICS_URI = os.environ.get(
    "DOLOS_ANALYTICS_URI", "postgresql+psycopg2://dolos:dolos@postgres:5432/dolos"
)

session = requests.Session()


def wait_for_api():
    for _ in range(60):
        try:
            if session.get(f"{BASE}/health", timeout=5).status_code == 200:
                return
        except requests.RequestException:
            pass
        time.sleep(3)
    sys.exit("Superset API never became ready")


def login():
    r = session.post(
        f"{BASE}/api/v1/security/login",
        json={"username": USER, "password": PW, "provider": "db", "refresh": True},
        timeout=15,
    )
    r.raise_for_status()
    token = r.json()["access_token"]
    session.headers.update({"Authorization": f"Bearer {token}"})


def get_or_create_database():
    existing = session.get(f"{BASE}/api/v1/database/", timeout=15).json()["result"]
    for db in existing:
        if db["database_name"] == "Dolos read models":
            print("database exists:", db["id"])
            return db["id"]
    r = session.post(
        f"{BASE}/api/v1/database/",
        json={
            "database_name": "Dolos read models",
            "sqlalchemy_uri": ANALYTICS_URI,
            "expose_in_sqllab": True,
        },
        timeout=30,
    )
    r.raise_for_status()
    db_id = r.json()["id"]
    print("created database:", db_id)
    return db_id


def find_by(endpoint, name_field, name_value):
    """List all of a resource (small counts here) and match by name — robust vs. rison filters."""
    res = session.get(f"{BASE}/api/v1/{endpoint}/?q=(page_size:200)", timeout=15).json().get(
        "result", []
    )
    for item in res:
        if item.get(name_field) == name_value:
            return item["id"]
    return None


def get_or_create_dataset(db_id, table_name, sql):
    existing = find_by("dataset", "table_name", table_name)
    if existing:
        print("dataset exists:", table_name, existing)
        return existing
    r = session.post(
        f"{BASE}/api/v1/dataset/",
        json={"database": db_id, "schema": "public", "table_name": table_name, "sql": sql},
        timeout=30,
    )
    r.raise_for_status()
    ds_id = r.json()["id"]
    print("created dataset:", table_name, ds_id)
    return ds_id


def get_or_create_table_chart(name, ds_id, columns):
    existing = find_by("chart", "slice_name", name)
    if existing:
        print("chart exists:", name, existing)
        return existing
    params = {
        "viz_type": "table",
        "query_mode": "raw",
        "all_columns": columns,
        "order_by_cols": [],
        "row_limit": 1000,
        "server_page_length": 25,
    }
    r = session.post(
        f"{BASE}/api/v1/chart/",
        json={
            "slice_name": name,
            "viz_type": "table",
            "datasource_id": ds_id,
            "datasource_type": "table",
            "params": json.dumps(params),
        },
        timeout=30,
    )
    r.raise_for_status()
    ch_id = r.json()["id"]
    print("created chart:", name, ch_id)
    return ch_id


def build_position(chart_ids_names):
    """A simple 2-per-row grid layout referencing the charts by id."""
    pos = {
        "DASHBOARD_VERSION_KEY": "v2",
        "ROOT_ID": {"type": "ROOT", "id": "ROOT_ID", "children": ["GRID_ID"]},
        "GRID_ID": {"type": "GRID", "id": "GRID_ID", "children": []},
    }
    row = None
    for i, (cid, name) in enumerate(chart_ids_names):
        if i % 2 == 0:
            row = f"ROW-{i}"
            pos[row] = {"type": "ROW", "id": row, "children": [], "meta": {"background": "BACKGROUND_TRANSPARENT"}}
            pos["GRID_ID"]["children"].append(row)
        cnode = f"CHART-{cid}"
        pos[cnode] = {
            "type": "CHART",
            "id": cnode,
            "children": [],
            "meta": {"chartId": cid, "width": 6, "height": 50, "sliceName": name},
        }
        pos[row]["children"].append(cnode)
    return pos


def get_or_create_dashboard(title, chart_ids_names):
    existing = find_by("dashboard", "dashboard_title", title)
    position = build_position(chart_ids_names)
    payload = {
        "dashboard_title": title,
        "published": True,
        "position_json": json.dumps(position),
        "json_metadata": json.dumps({"refresh_frequency": 0}),
    }
    if existing:
        dash_id = existing
        session.put(f"{BASE}/api/v1/dashboard/{dash_id}", json=payload, timeout=30).raise_for_status()
        print("dashboard updated:", dash_id)
    else:
        r = session.post(f"{BASE}/api/v1/dashboard/", json=payload, timeout=30)
        r.raise_for_status()
        dash_id = r.json()["id"]
        print("dashboard created:", dash_id)
    # Link each chart to the dashboard (the m2m relation is NOT derived from position_json alone).
    for cid, _ in chart_ids_names:
        session.put(
            f"{BASE}/api/v1/chart/{cid}", json={"dashboards": [dash_id]}, timeout=30
        ).raise_for_status()
    print("linked", len(chart_ids_names), "charts to dashboard", dash_id)
    return dash_id


def main():
    wait_for_api()
    login()
    db_id = get_or_create_database()

    ds_alerts = get_or_create_dataset(
        db_id,
        "bi_alerts_over_time",
        "SELECT date_trunc('hour', raised_at) AS bucket, severity, count(*) AS alerts"
        " FROM alert.alert_view GROUP BY 1, 2 ORDER BY 1",
    )
    ds_accounts = get_or_create_dataset(
        db_id,
        "bi_top_risk_accounts",
        "SELECT account_id, max(score) AS max_score, count(*) AS alert_count"
        " FROM alert.alert_view GROUP BY account_id ORDER BY max_score DESC, alert_count DESC",
    )
    ds_sar = get_or_create_dataset(
        db_id,
        "bi_sar_volume",
        "SELECT business_date, report_type, count(*) AS filings"
        " FROM reporting.filed_report GROUP BY 1, 2 ORDER BY 1",
    )
    ds_cases = get_or_create_dataset(
        db_id,
        "bi_case_throughput",
        "SELECT status, count(*) AS cases, round(avg(score), 1) AS avg_score"
        " FROM casework.case_view GROUP BY status ORDER BY cases DESC",
    )

    charts = [
        (get_or_create_table_chart("Alerts over time", ds_alerts, ["bucket", "severity", "alerts"]),
         "Alerts over time"),
        (get_or_create_table_chart("Top-risk accounts", ds_accounts, ["account_id", "max_score", "alert_count"]),
         "Top-risk accounts"),
        (get_or_create_table_chart("SAR/STR volume", ds_sar, ["business_date", "report_type", "filings"]),
         "SAR/STR volume"),
        (get_or_create_table_chart("Case throughput", ds_cases, ["status", "cases", "avg_score"]),
         "Case throughput"),
    ]

    get_or_create_dashboard("Dolos - Financial Crime Overview", charts)
    print("Superset provisioning complete.")


if __name__ == "__main__":
    main()
