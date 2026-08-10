#!/bin/sh
set -eu

databases="pera_identity pera_master_data pera_sales pera_finance pera_operations pera_activity pera_licensing"

for database in $databases; do
  exists="$(psql --dbname postgres --tuples-only --no-align --command "SELECT 1 FROM pg_database WHERE datname = '$database'")"
  if [ "$exists" != "1" ]; then
    createdb "$database"
  fi
done
