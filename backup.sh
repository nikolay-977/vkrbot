#!/bin/bash

BACKUP_DIR="./backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DB_NAME="admin_db"
DB_USER="admin_user"

# Создаем директорию для бэкапов если её нет
mkdir -p $BACKUP_DIR

# Делаем дамп базы данных
echo "Creating database backup..."
docker exec admin-app-postgres pg_dump -U $DB_USER $DB_NAME > "$BACKUP_DIR/backup_$TIMESTAMP.sql"

# Архивируем
gzip "$BACKUP_DIR/backup_$TIMESTAMP.sql"

# Удаляем старые бэкапы (старше 7 дней)
find $BACKUP_DIR -name "backup_*.sql.gz" -mtime +7 -delete

echo "Backup created: backup_$TIMESTAMP.sql.gz"