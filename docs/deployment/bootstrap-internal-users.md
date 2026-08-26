# Bootstrap internal users

`ADMIN` and `DISPATCHER` accounts cannot use public self-registration. A deployment
operator must create their rows in the `user` table through an approved administrative
database procedure.

Each internal row must use:

- a unique username;
- a password encoded with BCrypt (never plaintext);
- `status = ACTIVE`;
- exactly `role = ADMIN` or `role = DISPATCHER` as appropriate.

Do not put real passwords in this document, source code, deployment scripts, logs, or
Git history. Do not commit a production BCrypt hash whose corresponding default password
is known. Generate credentials and hashes in the target environment using the deployment
team's secret-handling procedure.

This project does not create default Java accounts and this increment performs no live or
cloud database operations.
