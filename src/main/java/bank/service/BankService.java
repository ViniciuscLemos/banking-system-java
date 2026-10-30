package bank.service;

import bank.model.Account;
import bank.model.Money;
import bank.model.Transaction;
import bank.repository.BankRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** The bank rules (validations, transfers...). The SQL lives in the repository. */
public class BankService {

    private final BankRepository repo;

    public BankService(BankRepository repo) {
        this.repo = repo;
    }

    public Account openAccount(String holder, String cpf, Account.AccountType type, BigDecimal deposit) {
        if (holder == null || holder.isBlank()) {
            throw new IllegalArgumentException("Account holder name is required");
        }
        String cleanCpf = Cpf.clean(cpf);
        if (!Cpf.isValid(cleanCpf)) {
            throw new IllegalArgumentException("Invalid CPF");
        }
        if (deposit == null || deposit.signum() < 0) {
            throw new IllegalArgumentException("Initial deposit can't be negative");
        }

        if (repo.findByCpf(cleanCpf).isPresent()) {
            throw new IllegalStateException("There's already an account with this CPF");
        }

        Account account = new Account(holder.trim(), cleanCpf, type, deposit);

        return repo.inTransaction(() -> {
            repo.saveAccount(account);

            if (deposit.signum() > 0) {
                repo.saveTransaction(new Transaction(
                    account.getId(), null,
                    Transaction.TransactionType.DEPOSIT,
                    deposit, account.getBalance(), "Initial deposit when opening the account"
                ));
            }
            return account;
        });
    }

    public Account deposit(String accountId, BigDecimal amount, String description) {
        Account account = findOrThrow(accountId);
        account.deposit(amount);

        return repo.inTransaction(() -> {
            repo.updateAccount(account);
            repo.saveTransaction(new Transaction(
                account.getId(), null,
                Transaction.TransactionType.DEPOSIT,
                amount, account.getBalance(),
                description != null ? description : "Deposit"
            ));
            return account;
        });
    }

    public Account withdraw(String accountId, BigDecimal amount, String description) {
        Account account = findOrThrow(accountId);
        account.withdraw(amount);

        return repo.inTransaction(() -> {
            repo.updateAccount(account);
            repo.saveTransaction(new Transaction(
                account.getId(), null,
                Transaction.TransactionType.WITHDRAWAL,
                amount, account.getBalance(),
                description != null ? description : "Withdrawal"
            ));
            return account;
        });
    }

    /** Both accounts and both statement lines are saved together, in a single transaction. */
    public void transfer(String sourceId, String targetId, BigDecimal amount) {
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Source and target accounts can't be the same");
        }

        Account source = findOrThrow(sourceId);
        Account target = findOrThrow(targetId);

        if (!target.isActive()) {
            throw new IllegalStateException("Target account is closed");
        }

        source.withdraw(amount);
        target.deposit(amount);

        repo.inTransaction(() -> {
            repo.updateAccount(source);
            repo.updateAccount(target);

            repo.saveTransaction(new Transaction(
                source.getId(), target.getId(),
                Transaction.TransactionType.TRANSFER_OUT,
                amount, source.getBalance(),
                "Transfer to " + target.getId() + " (" + target.getHolder() + ")"
            ));
            repo.saveTransaction(new Transaction(
                target.getId(), source.getId(),
                Transaction.TransactionType.TRANSFER_IN,
                amount, target.getBalance(),
                "Transfer from " + source.getId() + " (" + source.getHolder() + ")"
            ));
            return null;
        });
    }

    public Account closeAccount(String accountId) {
        Account account = findOrThrow(accountId);
        account.close();
        repo.updateAccount(account);
        return account;
    }

    public List<Transaction> getStatement(String accountId) {
        findOrThrow(accountId);
        return repo.listStatement(accountId);
    }

    /** Only the lines from `from` on, for "the last N days" in the menu. */
    public static List<Transaction> since(List<Transaction> statement, LocalDateTime from) {
        return statement.stream().filter(t -> !t.getCreatedAt().isBefore(from)).toList();
    }

    public record Totals(BigDecimal moneyIn, BigDecimal moneyOut) {}

    /** What came in and what went out, for the bottom of the statement. */
    public static Totals totals(List<Transaction> statement) {
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        for (Transaction t : statement) {
            if (t.isCredit()) {
                in = in.add(t.getAmount());
            } else {
                out = out.add(t.getAmount());
            }
        }
        return new Totals(in, out);
    }

    public List<Account> listAccounts() {
        return repo.listAll();
    }

    public Optional<Account> findById(String id) {
        return repo.findById(id);
    }

    /** Nobody remembers an 8 character ID, so the menu also takes the CPF (with or without dots). */
    public Optional<Account> findByIdOrCpf(String text) {
        String clean = text.trim().toUpperCase();
        Optional<Account> byId = repo.findById(clean);
        if (byId.isPresent() || !Cpf.isValid(clean)) {
            return byId;
        }
        return repo.findByCpf(Cpf.clean(clean));
    }

    public BigDecimal totalBalance() {
        return listAccounts().stream()
            .filter(Account::isActive)
            .map(Account::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Account findOrThrow(String id) {
        return repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    public static String format(BigDecimal amount) {
        return Money.format(amount);
    }
}
