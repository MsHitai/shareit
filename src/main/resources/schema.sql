CREATE TABLE IF NOT EXISTS users (
	id BIGINT PRIMARY KEY NOT NULL AUTO_INCREMENT,
	name VARCHAR(255) NOT NULL,
	email VARCHAR(512) NOT NULL,
	CONSTRAINT UQ_USER_EMAIL UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS items (
	id BIGINT PRIMARY KEY NOT NULL AUTO_INCREMENT,
	user_id BIGINT,
	name VARCHAR(200) NOT NULL,
	description VARCHAR(200) NOT NULL,
	available BOOLEAN NOT NULL,
	CONSTRAINT fk_items_to_users FOREIGN KEY(user_id) REFERENCES users(id)
);