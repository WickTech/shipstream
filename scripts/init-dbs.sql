-- Creates one database per service (shared Postgres instance for local dev)
CREATE DATABASE orderdb;
CREATE DATABASE shipmentdb;
CREATE DATABASE trackingdb;
CREATE DATABASE notificationdb;

GRANT ALL PRIVILEGES ON DATABASE orderdb        TO shipstream;
GRANT ALL PRIVILEGES ON DATABASE shipmentdb     TO shipstream;
GRANT ALL PRIVILEGES ON DATABASE trackingdb     TO shipstream;
GRANT ALL PRIVILEGES ON DATABASE notificationdb TO shipstream;
