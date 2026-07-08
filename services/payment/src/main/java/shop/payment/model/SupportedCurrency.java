package shop.payment.model;

import java.util.Currency;
import java.util.Locale;

public enum SupportedCurrency {
    UAH("UAH"),
    USD("USD"),
    EUR("EUR");

    private final Currency currency;

    SupportedCurrency(String code) {
        this.currency = Currency.getInstance(code);
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getCode() {
        return currency.getCurrencyCode();
    }

    public String getSymbol() {
        return currency.getSymbol();
    }

    public String getSymbol(Locale locale) {
        return currency.getSymbol(locale);
    }

    public String getDisplayName(Locale locale) {
        return currency.getDisplayName(locale);
    }
}
