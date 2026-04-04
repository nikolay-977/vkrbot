#!/bin/bash

if [ -z "$1" ]; then
    echo "Usage: ./restore.sh <backup_file>"
    exit 1
fi

BACKUP_FILE=$1
DB_NAME="admin_db"
DB_USER="admin_user"

if [ ! -f "$BACKUP_FILE" ]; then
    echo "Backup file not found: $BACKUP_FILE"
    exit 1
fi

echo "Restoring database from $BACKUP_FILE..."

# Разархивируем если нужно
if [[ $BACKUP_FILE == *.gz ]]; then
    gunzip -c "$BACKUP_FILE" | docker exec -i admin-app-postgres psql -U $DB_USER $DB_NAME
else
    cat "$BACKUP_FILE" | docker exec -i admin-app-postgres psql -U $DB_USER $DB_NAME
fi

echo "Restore complete!"