SHOW DATABASES LIKE 'future_minds';
USE future_minds;
SHOW TABLES;
SHOW GRANTS FOR 'future_minds';

SELECT *
FROM flyway_schema_history
ORDER BY installed_rank;