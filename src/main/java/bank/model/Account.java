package bank.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

public class Account {

    public enum AccountType {
        CHECKING,
        SAVINGS;

        /** "1", "2", "checking" or "savings" (any case); null for anything else. */
        public static AccountType parse(String text) {
            String t = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (t) {
                case "1", "checking", "c" -> CHECKING;
                case "2", "savings", "s" -> SAVINGS;
                default -> null;
            };
        }
    }

    private final String id;
    private final String holder;
    private final String cpf;
    private final AccountType type;
    private BigDecimal balance;
    private boolean active;
    private final LocalDateTime createdAt;

    /** New account: generates the id (first 8 characters of a UUID). */
    public Account(String holder, String cpf, AccountType type, BigDecimal initialBalance) {
        this(UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
             holder, cpf, type, initialBalance, true, LocalDateTime.now());
    }

    /** Account that already exists in the database. */
    public Account(String id, String holder, String cpf, AccountType type,
                   BigDecimal balance, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.holder = holder;
        this.cpf = cpf;
        this.type = type;
        this.balance = money(balance);
        this.active = active;
        this.createdAt = createdAt;
    }

    public String getId()               { return id; }
    public String getHolder()           { return holder; }
    public String getCpf()              { return cpf; }
    public AccountType getType()        { return type; }
    public BigDecimal getBalance()      { return balance; }
    public boolean isActive()           { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void deposit(BigDecimal amount) {
        validateAmount(amount, "Deposit");
        requireActive();
        this.balance = this.balance.add(money(amount));
    }

    public void withdraw(BigDecimal amount) {
        validateAmount(amount, "Withdrawal");
        requireActive();
        if (amount.compareTo(this.balance) > 0) {
            throw new IllegalStateException("Insufficient funds. Current balance: " + Money.format(balance));
        }
        this.balance = this.balance.subtract(money(amount));
    }

    /** Can only be closed with a zero balance. */
    public void close() {
        requireActive();
        if (balance.signum() != 0) {
            throw new IllegalStateException("Withdraw or transfer the balance of " + Money.format(balance)
                    + " before closing the account");
        }
        this.active = false;
    }

    private void requireActive() {
        if (!active) {
            throw new IllegalStateException("Account is closed");
        }
    }

    private static void validateAmount(BigDecimal amount, String operation) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(operation + " amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Use at most 2 decimal places");
        }
    }

    /** Rounds to cents. */
    public static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN);
    }

    @Override
    public String toString() {
        return String.format(
            "Account[%s] %s | Type: %s | Balance: %s | %s",
            id, holder, type, Money.format(balance), active ? "Active" : "Closed"
        );
    }
}
