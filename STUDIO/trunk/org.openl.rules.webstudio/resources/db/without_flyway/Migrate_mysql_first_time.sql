ALTER TABLE OpenLUser CHANGE COLUMN Privileges UserPrivileges LONGTEXT NULL DEFAULT NULL;

ALTER TABLE UserGroup CHANGE COLUMN Privileges UserPrivileges LONGTEXT NULL DEFAULT NULL;