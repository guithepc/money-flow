CREATE TABLE owner (
	owner_id SERIAL PRIMARY KEY,
	name varchar(255) NOT NULL,
    password_hash CHAR(60) NOT NULL,
	email varchar (255) NOT NULL,
    CONSTRAINT owner_email_key UNIQUE (email),
    telegram_chat_id BIGINT UNIQUE
);

CREATE TABLE account (
	account_id SERIAL PRIMARY KEY,
	owner_id INT NOT NULL REFERENCES owner(owner_id),
	description varchar (255) NOT NULL,
	balance NUMERIC NOT NULL
);

CREATE TABLE category (
	category_id SERIAL PRIMARY KEY,
	description varchar (255) NOT NULL
);

drop table recurring_payment;
drop table transactions;
DROP TYPE frequency;
CREATE TYPE frequency as ENUM ('MONTHLY', 'WEEKLY', 'ANNUAL');

CREATE TABLE recurring_payment (
	recurring_payment_id SERIAL PRIMARY KEY,
	owner_id INT NOT NULL REFERENCES owner(owner_id),
	account_id INT NOT NULL REFERENCES account(account_id),
	category_id INT NOT NULL REFERENCES category(category_id),
	description varchar (255) NOT NULL,
	is_active BOOLEAN NOT NULL,
	recurring_start_date DATE NOT NULL,
	recurring_end_date DATE,
	amount NUMERIC NOT NULL,
	frequency frequency NOT NULL
);


CREATE TYPE operation as ENUM ('EXPENSE', 'INCOME', 'TRANSFER');

CREATE TABLE transactions (
	transactions_id SERIAL PRIMARY KEY,
	owner_id INT NOT NULL REFERENCES owner(owner_id),
	account_id INT NOT NULL REFERENCES account(account_id),
	category_id INT NOT NULL REFERENCES category(category_id),
	recurring_payment_id INT REFERENCES recurring_payment(recurring_payment_id),
	description varchar (255) NOT NULL,
	timestamp DATE NOT NULL,
	amount NUMERIC NOT NULL,
	operation_type operation NOT NULL
);