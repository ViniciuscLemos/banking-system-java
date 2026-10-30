package bank;

import bank.model.Account;
import bank.model.Transaction;
import bank.repository.BankRepository;
import bank.service.BankService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// service + repository using a real SQLite database
class BankServiceTest {

    // made up CPFs, but valid ones
    static final String CPF_MARIA = "52998224725";
    static final String CPF_JOAO = "11144477735";

    @TempDir
    Path folder;

    Connection conn;
    BankService service;

    @BeforeEach
    void open() throws SQLException {
        conn = connect();
        service = new BankService(new BankRepository(conn));
    }

    @AfterEach
    void close() throws SQLException {
        conn.close();
    }

    Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("test.db"));
    }

    static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    @Test
    void opensAccountWithInitialDeposit() {
        Account account = service.openAccount("Maria", "529.982.247-25", Account.AccountType.CHECKING, money("100"));

        Account saved = service.findById(account.getId()).orElseThrow();
        assertEquals(account.getId(), saved.getId());
        assertEquals(CPF_MARIA, saved.getCpf());  // CPF saved without punctuation
        assertEquals(money("100.00"), saved.getBalance());
        assertEquals(1, service.getStatement(account.getId()).size());
    }

    @Test
    void validatesOpeningData() {
        assertThrows(IllegalArgumentException.class,
            () -> service.openAccount(" ", CPF_MARIA, Account.AccountType.CHECKING, money("0")));
        assertThrows(IllegalArgumentException.class,
            () -> service.openAccount("Maria", "12345678901", Account.AccountType.CHECKING, money("0")));
        assertThrows(IllegalArgumentException.class,
            () -> service.openAccount("Maria", CPF_MARIA, Account.AccountType.CHECKING, money("-1")));

        service.openAccount("Maria", CPF_MARIA, Account.AccountType.CHECKING, money("0"));
        assertThrows(IllegalStateException.class,
            () -> service.openAccount("Someone else", CPF_MARIA, Account.AccountType.CHECKING, money("0")));
    }

    @Test
    void depositAndWithdrawalPersistTheBalance() {
        Account account = service.openAccount("Maria", CPF_MARIA, Account.AccountType.CHECKING, money("100"));

        service.deposit(account.getId(), money("50.25"), null);
        service.withdraw(account.getId(), money("20.10"), null);

        assertEquals(money("130.15"), service.findById(account.getId()).orElseThrow().getBalance());
    }

    @Test
    void amountsAreExact() {
        // With double, 0.1 + 0.2 would be 0.30000000000000004
        Account account = service.openAccount("Maria", CPF_MARIA, Account.AccountType.CHECKING, money("0.10"));
        service.deposit(account.getId(), money("0.20"), null);
        assertEquals(money("0.30"), service.findById(account.getId()).orElseThrow().getBalance());
    }

    @Test
    void cantWithdrawMoreThanTheBalance() {
        Account account = service.openAccount("Maria", CPF_MARIA, Account.AccountType.CHECKING, money("10"));
        assertThrows(IllegalStateException.class, () -> service.withdraw(account.getId(), money("10.01"), null));
        assertThrows(IllegalArgumentException.class, () -> service.deposit(account.getId(), money("0"), null));
        assertEquals(money("10.00"), service.findById(account.getId()).orElseThrow().getBalance());
    }

    @Test
    void transferMovesBalanceAndAddsOneLinePerAccount() {
        Account maria = service.openAccount("Maria", CPF_MARIA, Account.AccountType.CHECKING, money("1500"));
        Account joao = service.openAccount("João", CPF_JOAO, Account.AccountType.SAVINGS, money("800"));

        service.transfer(maria.getId(), joao.getId(), money("300"));

        assertEquals(money("1200.00"), service.findById(maria.getId()).orElseThrow().getBalance());
        assertEquals(money("1100.00"), service.findById(joao.getId()).orElseThrow().getBalance());

        List<Transaction> mariaStatement = service.getStatement(maria.getId());
        assertEquals(2, mariaStatement.size());
        assertEquals(Transaction.TransactionType.TRANSFER_OUT, mariaStatement.get(0).getType());
        assertEquals(joao.getId(), mariaStatement.get(0).getOtherAccountId());

        List<Transaction> joaoStatement = service.getStatement(joao.getId());
        assertEquals(2, joaoStatement.size());
        assertEquals(Transaction.TransactionType.TRANSFER_IN, joaoStatement.get(0).getType());
    }

    @Test
    void invalidTransferChangesNothing() {
        Account maria = service.openAccount("Maria", CPF_MARIA, Account.AccountType.CHECKING, money("100"));
        Account joao = service.openAccount("João", CPF_JOAO, Account.AccountType.CHECKING, money("0"));

        assertThrows(IllegalStateException.class, () -> service.transfer(maria.getId(), joao.getId(), money("500")));
        assertThrows(IllegalArgumentException.class, () -> service.transfer(maria.getId(), maria.getId(), money("1")));
        assertThrows(IllegalArgumentException.class, () -> service.transfer(maria.getId(), "NOTEXIST", money("1")));

        assertEquals(money("100.00"), service.findById(maria.getId()).orElseThrow().getBalance());
        assertEquals(1, service.getStatement(maria.getId()).size());
    }

    @Test
    void closesAccountOnlyWithZeroBalance() {
        Account account = service.openAccount("Maria", CPF_MARIA, Account.AccountType.CHECKING, money("10"));
        assertThrows(IllegalStateException.class, () -> service.closeAccount(account.getId()));

        service.withdraw(account.getId(), money("10"), null);
        service.closeAccount(account.getId());

        assertFalse(service.findById(account.getId()).orElseThrow().isActive());
        assertThrows(IllegalStateException.class, () -> service.deposit(account.getId(), money("1"), null));
    }

    @Test
    void dataSurvivesReopeningTheProgram() throws SQLException {
        Account account = service.openAccount("Maria", CPF_MARIA, Account.AccountType.CHECKING, money("100"));
        service.deposit(account.getId(), money("1"), null);
        conn.close();

        // close and open again, as if it were another run of the program
        conn = connect();
        service = new BankService(new BankRepository(conn));

        service.deposit(account.getId(), money("2"), null);
        service.deposit(account.getId(), money("3"), null);

        Account reopened = service.findById(account.getId()).orElseThrow();
        assertEquals(money("106.00"), reopened.getBalance());
        assertEquals(account.getCreatedAt(), reopened.getCreatedAt());
        assertEquals(4, service.getStatement(account.getId()).size());
    }

    @Test
    void totalBalanceAddsActiveAccounts() {
        Account maria = service.openAccount("Maria", CPF_MARIA, Account.AccountType.CHECKING, money("10.50"));
        service.openAccount("João", CPF_JOAO, Account.AccountType.CHECKING, money("4.50"));
        assertEquals(money("15.00"), service.totalBalance());

        service.withdraw(maria.getId(), money("10.50"), null);
        service.closeAccount(maria.getId());
        assertEquals(money("4.50"), service.totalBalance());
    }

    @Test
    void statementTotals() {
        Account maria = service.openAccount("Maria", CPF_MARIA, Account.AccountType.CHECKING, money("100"));
        Account joao = service.openAccount("João", CPF_JOAO, Account.AccountType.CHECKING, money("0"));
        service.withdraw(maria.getId(), money("20"), null);
        service.transfer(maria.getId(), joao.getId(), money("30.50"));
        service.deposit(maria.getId(), money("5"), null);

        BankService.Totals totals = BankService.totals(service.getStatement(maria.getId()));
        assertEquals(money("105.00"), totals.moneyIn());
        assertEquals(money("50.50"), totals.moneyOut());
    }

    @Test
    void statementSinceADay() {
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 10, 30, 12, 0);
        List<Transaction> statement = List.of(
            new Transaction("1", "A", null, Transaction.TransactionType.DEPOSIT, money("10"), money("10"), "new", now),
            new Transaction("2", "A", null, Transaction.TransactionType.DEPOSIT, money("5"), money("5"), "old", now.minusDays(40))
        );
        List<Transaction> recent = BankService.since(statement, now.minusDays(30));
        assertEquals(1, recent.size());
        assertEquals("new", recent.get(0).getDescription());
        assertEquals(2, BankService.since(statement, now.minusDays(40)).size());
    }

    @Test
    void findsAccountByIdOrCpf() {
        Account maria = service.openAccount("Maria", CPF_MARIA, Account.AccountType.CHECKING, money("0"));
        assertEquals(maria.getId(), service.findByIdOrCpf(maria.getId().toLowerCase()).orElseThrow().getId());
        assertEquals(maria.getId(), service.findByIdOrCpf("529.982.247-25").orElseThrow().getId());
        assertTrue(service.findByIdOrCpf(CPF_JOAO).isEmpty());
        assertTrue(service.findByIdOrCpf("nope").isEmpty());
    }
}
