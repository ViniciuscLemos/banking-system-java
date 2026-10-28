# Banking System

![Tests](https://github.com/ViniciuscLemos/banking-system-java/actions/workflows/tests.yml/badge.svg)

A simple bank that runs in the terminal, written in Java with SQLite (JDBC).

You can open an account, deposit, withdraw, transfer between accounts, see the statement and close an account. The data is saved to a `bank.db` file, so it's still there when you open the program again.

## Running

You need JDK 17 and Maven.

```bash
mvn package
java -jar target/banking-system.jar
```

The first time it runs, it creates two sample accounts for you to play with.

To run only the tests:

```bash
mvn test
```

## What it looks like

A withdrawal, a typo in the amount and the statement, with money in shown in green and money out in red (and the total of each at the bottom):

![A withdrawal, an invalid amount and the statement in the terminal](docs/screenshot.png)

Opening an account (the CPF check digits are validated, so a made-up number is refused), a transfer and the account list:

![Opening an account with an invalid CPF, then a valid one, a transfer and the list of accounts](docs/screenshot-accounts.png)

Wherever it asks for the account you can type the ID or the CPF (with or without the dots), since nobody remembers an ID like `B9117A50`. Right after that, the program shows whose account it is. If you get the ID wrong, you find out right away, not after typing the amount. And the transfer only happens after you confirm seeing the name of who's getting the money.

On Windows, if accented names look weird in the terminal, run `chcp 65001` first.

The colors only show up in a real terminal, so `java -jar banking-system.jar > out.txt` gives a clean file. Set `NO_COLOR=1` to turn them off or `FORCE_COLOR=1` to keep them when piping.

## Some decisions

- I used `BigDecimal` for money instead of `double`, because `double` has rounding errors (like `0.1 + 0.2` being `0.30000000000000004`).
- The transfer runs inside a database transaction: either both accounts are updated, or neither is.
- Each account is tied to a CPF (the Brazilian taxpayer ID). It's validated by its check digits, and you can't open two accounts with the same CPF.
- When typing amounts, you can use `1500`, `1500.50` or `1,500.50`.

## Structure

I split it into layers:

```
Main (menu) -> BankService (rules) -> BankRepository (SQL) -> SQLite
```

```
src/main/java/bank/
  Main.java
  model/        Account, Transaction, Money
  repository/   BankRepository
  service/      BankService, Cpf
src/test/java/bank/
```
