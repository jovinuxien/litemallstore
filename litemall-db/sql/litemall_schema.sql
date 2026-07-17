drop database if exists litemall;
drop user if exists 'litemall'@'%';
-- 支持emoji：需要mysql数据库参数： character_set_server=utf8mb4
create database litemall default character set utf8mb4 collate utf8mb4_unicode_ci;
use litemall;
-- Wave 7: the real password was committed here. Substitute your own before
-- running this, e.g.  sed "s/__MYSQL_PASSWORD__/$MYSQL_PASSWORD/" litemall_schema.sql | mysql
-- Never commit a real value back into this file.
create user 'litemall'@'localhost' identified by '__MYSQL_PASSWORD__';
grant all privileges on litemall.* to 'litemall'@'localhost' with grant option;
flush privileges;