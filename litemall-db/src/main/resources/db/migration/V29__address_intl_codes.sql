-- Customer address book: real-world postal/area codes don't fit char(6)
-- ("SW1A 1AA" is 8 chars, US ZIP+4 is 10), so /srv/address/save blew up with a
-- data-too-long error on any non-6-char code. Widen both code columns; values
-- stay free-form strings.
ALTER TABLE `litemall_address`
    MODIFY COLUMN `postal_code` VARCHAR(20) NULL COMMENT '邮政编码',
    MODIFY COLUMN `area_code` VARCHAR(20) NULL COMMENT '地区编码';
