SHOW STATUS LIKE 'Threads_connected';
SHOW VARIABLES LIKE 'max_connections';
SHOW STATUS LIKE 'Connections';
SHOW FULL PROCESSLIST;

SELECT SUBSTRING_INDEX(host, ':', 1) AS ip_address, COUNT(*) AS connections
FROM information_schema.PROCESSLIST
GROUP BY ip_address
ORDER BY connections DESC;
