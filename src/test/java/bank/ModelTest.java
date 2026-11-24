package bank;

import bank.model.Account;
import bank.model.Money;
import bank.service.Cpf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "529.982.247-25", "11144477735"})
    void validCpfs(String cpf) {
        assertTrue(Cpf.isValid(cpf));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678901", "11111111111", "5299822472", "529982247250", "abc", ""})
    void invalidCpfs(String cpf) {
        assertFalse(Cpf.isValid(cpf));
    }

    @Test
    void accountTypeFromWhatWasTyped() {
        assertEquals(Account.AccountType.CHECKING, Account.AccountType.parse(" 1 "));
        assertEquals(Account.AccountType.SAVINGS, Account.AccountType.parse("Savings"));
        assertNull(Account.AccountType.parse("3"));
        assertNull(Account.AccountType.parse(""));
    }

    @Test
    void formatsAsDollars() {
        assertEquals("$1,234.56", Money.format(new BigDecimal("1234.56")));
        assertEquals("$0.00", Money.format(BigDecimal.ZERO));
    }

    @Test
    void readsAmountsInDifferentFormats() {
        assertEquals(new BigDecimal("1500.50"), Money.parse("1,500.50"));
        assertEquals(new BigDecimal("1500.50"), Money.parse("1500.50"));
        assertEquals(new BigDecimal("1500.50"), Money.parse("$ 1500.50"));
        assertEquals(new BigDecimal("1500.50"), Money.parse("1500,50"));
        assertEquals(new BigDecimal("1500.50"), Money.parse("1.500,50"));
        assertEquals(new BigDecimal("1234567.89"), Money.parse("1.234.567,89"));
        assertEquals(new BigDecimal("1500"), Money.parse("1,500"));
        assertEquals(new BigDecimal("1.5"), Money.parse("1.5"));
        assertEquals(new BigDecimal("10"), Money.parse(" 10 "));
        assertThrows(NumberFormatException.class, () -> Money.parse("ten"));
        assertThrows(NumberFormatException.class, () -> Money.parse("1e5"));
        assertThrows(NumberFormatException.class, () -> Money.parse("+10"));
        assertThrows(NumberFormatException.class, () -> Money.parse("-5"));
    }

    @Test
    void accountRejectsInvalidAmounts() {
        Account account = new Account("Maria", "52998224725", Account.AccountType.CHECKING, new BigDecimal("10"));
        assertThrows(IllegalArgumentException.class, () -> account.deposit(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> account.deposit(new BigDecimal("0.001")));
        assertThrows(IllegalStateException.class, () -> account.withdraw(new BigDecimal("10.01")));
        account.deposit(new BigDecimal("0.50"));
        assertEquals(new BigDecimal("10.50"), account.getBalance());
    }
}
