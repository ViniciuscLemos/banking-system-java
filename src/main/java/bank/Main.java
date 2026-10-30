package bank;

import bank.model.Account;
import bank.model.Money;
import bank.model.Transaction;
import bank.repository.BankRepository;
import bank.service.BankService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Terminal menu. */
public class Main {

    private static BankService service;
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        // you can pass another file: java -jar banking-system.jar other.db
        String file = args.length > 0 ? args[0] : "bank.db";
        String url = "jdbc:sqlite:" + file;

        try (Connection conn = DriverManager.getConnection(url)) {
            System.out.println("Database connected: " + file);

            BankRepository repo = new BankRepository(conn);
            service = new BankService(repo);

            createSampleData();

            boolean running = true;
            while (running) {
                showMenu();
                String option = scanner.nextLine().trim();

                switch (option) {
                    case "1" -> openAccount();
                    case "2" -> makeDeposit();
                    case "3" -> makeWithdrawal();
                    case "4" -> makeTransfer();
                    case "5" -> showStatement();
                    case "6" -> listAccounts();
                    case "7" -> closeAccount();
                    case "0" -> {
                        System.out.println("\nShutting down. Bye!");
                        running = false;
                    }
                    default -> System.out.println(Colors.red("Invalid option. Try again."));
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        } catch (NoSuchElementException e) {
            // Ctrl+D / Ctrl+Z
            System.out.println("\nInput closed. Bye!");
        }
    }

    private static void showMenu() {
        System.out.println("\n=============================");
        System.out.println("   BANKING SYSTEM");
        System.out.println("=============================");
        System.out.println("1. Open new account");
        System.out.println("2. Deposit");
        System.out.println("3. Withdraw");
        System.out.println("4. Transfer");
        System.out.println("5. Statement");
        System.out.println("6. List accounts");
        System.out.println("7. Close account");
        System.out.println("0. Quit");
        System.out.print("\nChoose: ");
    }

    private static void openAccount() {
        System.out.println("\n--- OPEN ACCOUNT ---");
        System.out.print("Account holder name: ");
        String name = scanner.nextLine();
        System.out.print("CPF: ");
        String cpf = scanner.nextLine();
        System.out.print("Type (1-Checking / 2-Savings): ");
        String typeStr = scanner.nextLine().trim();
        Account.AccountType type = typeStr.equals("2") ? Account.AccountType.SAVINGS : Account.AccountType.CHECKING;
        System.out.print("Initial deposit (0 for none): $");
        BigDecimal deposit = readAmount();
        if (deposit == null) return;

        try {
            Account account = service.openAccount(name, cpf, type, deposit);
            System.out.println("\nAccount opened!");
            System.out.println("Your account ID: " + account.getId());
            System.out.println("Write this ID down, it's used in every operation.");
        } catch (RuntimeException e) {
            System.out.println(Colors.red("Error: " + e.getMessage()));
        }
    }

    private static void makeDeposit() {
        System.out.println("\n--- DEPOSIT ---");
        Account target = readAccount("Account ID or CPF: ");
        if (target == null) return;
        String id = target.getId();
        System.out.print("Amount: $");
        BigDecimal amount = readAmount();
        if (amount == null) return;
        System.out.print("Description (Enter for default): ");
        String desc = scanner.nextLine().trim();

        try {
            Account account = service.deposit(id, amount, desc.isEmpty() ? null : desc);
            System.out.println("\nDeposit done! New balance: " + Money.format(account.getBalance()));
        } catch (RuntimeException e) {
            System.out.println(Colors.red("Error: " + e.getMessage()));
        }
    }

    private static void makeWithdrawal() {
        System.out.println("\n--- WITHDRAW ---");
        Account target = readAccount("Account ID or CPF: ");
        if (target == null) return;
        String id = target.getId();
        System.out.print("Amount: $");
        BigDecimal amount = readAmount();
        if (amount == null) return;

        try {
            Account account = service.withdraw(id, amount, "ATM withdrawal");
            System.out.println("\nWithdrawal done! New balance: " + Money.format(account.getBalance()));
        } catch (RuntimeException e) {
            System.out.println(Colors.red("Error: " + e.getMessage()));
        }
    }

    private static void makeTransfer() {
        System.out.println("\n--- TRANSFER ---");
        Account source = readAccount("Source account (ID or CPF): ");
        if (source == null) return;
        Account target = readAccount("Target account (ID or CPF): ");
        if (target == null) return;
        System.out.print("Amount: $");
        BigDecimal amount = readAmount();
        if (amount == null) return;

        // shows who's receiving it first, like banking apps do
        System.out.printf("Transfer %s from %s to %s? (y/N): ",
            Money.format(amount), source.getHolder(), target.getHolder());
        if (!scanner.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("Cancelled.");
            return;
        }

        try {
            service.transfer(source.getId(), target.getId(), amount);
            System.out.println("\nTransfer done!");
        } catch (RuntimeException e) {
            System.out.println(Colors.red("Error: " + e.getMessage()));
        }
    }

    private static void showStatement() {
        System.out.println("\n--- STATEMENT ---");
        Account account = readAccount("Account ID or CPF: ");
        if (account == null) return;

        try {
            List<Transaction> statement = service.getStatement(account.getId());
            System.out.print("Only the last N days? (Enter for everything): ");
            String days = scanner.nextLine().trim();
            if (!days.isEmpty()) {
                int n;
                try {
                    n = Integer.parseInt(days);
                } catch (NumberFormatException e) {
                    n = -1;
                }
                if (n < 1) {
                    System.out.println(Colors.red("Type a number of days, like 30. Cancelled."));
                    return;
                }
                statement = BankService.since(statement, java.time.LocalDate.now().minusDays(n - 1L).atStartOfDay());
            }

            List<String> lines = statement.stream().map(Transaction::toString).toList();
            // the line follows the longest row, since the descriptions vary in size
            int width = Math.max(60, lines.stream().mapToInt(String::length).max().orElse(0));
            String line = "-".repeat(width);

            System.out.println("\nStatement for account " + account.getId() + " (" + account.getHolder() + ")");
            System.out.println(line);
            if (lines.isEmpty()) {
                System.out.println("No transactions found.");
            } else {
                // the width above is measured on the plain lines; the colors go in after
                statement.forEach(t -> System.out.println(colored(t)));
            }
            System.out.println(line);
            if (!statement.isEmpty()) {
                BankService.Totals totals = BankService.totals(statement);
                System.out.println("Money in:  " + Colors.green(Money.format(totals.moneyIn())));
                System.out.println("Money out: " + Colors.red(Money.format(totals.moneyOut())));
            }
            System.out.println("Current balance: " + Money.format(account.getBalance()));
        } catch (RuntimeException e) {
            System.out.println(Colors.red("Error: " + e.getMessage()));
        }
    }

    /** The statement line with the amount in green (money in) or red (money out). */
    static String colored(Transaction t) {
        String plain = t.toString();
        String amount = String.format("%s%12s", t.isCredit() ? "+" : "-", Money.format(t.getAmount()));
        String painted = t.isCredit() ? Colors.green(amount) : Colors.red(amount);
        return plain.replaceFirst(java.util.regex.Pattern.quote(amount), java.util.regex.Matcher.quoteReplacement(painted));
    }

    private static void listAccounts() {
        List<Account> accounts = service.listAccounts();
        System.out.println("\n--- ACCOUNTS ---");
        if (accounts.isEmpty()) {
            System.out.println("No accounts yet.");
        } else {
            accounts.forEach(a -> System.out.println("  " + a));
            System.out.println("\nTotal balance in active accounts: " + Money.format(service.totalBalance()));
        }
    }

    private static void closeAccount() {
        System.out.println("\n--- CLOSE ACCOUNT ---");
        Account account = readAccount("Account ID or CPF: ");
        if (account == null) return;
        System.out.print("Close the account of " + account.getHolder() + "? (y/N): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("Cancelled.");
            return;
        }
        try {
            service.closeAccount(account.getId());
            System.out.println("Account closed.");
        } catch (RuntimeException e) {
            System.out.println(Colors.red("Error: " + e.getMessage()));
        }
    }

    private static void createSampleData() {
        if (!service.listAccounts().isEmpty()) return;

        System.out.println("Creating sample accounts...");
        // made up CPFs, but valid ones
        Account a1 = service.openAccount("Maria Silva", "529.982.247-25", Account.AccountType.CHECKING, new BigDecimal("1500.00"));
        Account a2 = service.openAccount("João Santos", "111.444.777-35", Account.AccountType.SAVINGS, new BigDecimal("800.00"));
        service.transfer(a1.getId(), a2.getId(), new BigDecimal("300.00"));
        System.out.println("Sample accounts created! IDs: " + a1.getId() + ", " + a2.getId());
    }

    /** Asks for the ID and checks right away that the account exists, before asking the rest. */
    private static Account readAccount(String prompt) {
        System.out.print(prompt);
        String id = scanner.nextLine().trim();
        Account account = service.findByIdOrCpf(id).orElse(null);
        if (account == null) {
            System.out.println(Colors.red("Account not found: " + id));
        } else {
            System.out.println("  " + account.getHolder() + (account.isActive() ? "" : " (closed)"));
        }
        return account;
    }

    /** Reads an amount. Returns null (and says so) if the text isn't a number. */
    private static BigDecimal readAmount() {
        String text = scanner.nextLine();
        try {
            return Money.parse(text);
        } catch (NumberFormatException e) {
            System.out.println(Colors.red("Invalid amount: \"" + text.trim() + "\". Cancelled."));
            return null;
        }
    }
}
