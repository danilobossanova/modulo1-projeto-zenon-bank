package br.com.zenon.fraud;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Cliente tal como participa de uma transação, com os saldos imediatamente antes e depois dela.
 *
 * <p>Agrupa as colunas {@code nameOrig}/{@code oldbalanceOrg}/{@code newbalanceOrig} e
 * {@code nameDest}/{@code oldbalanceDest}/{@code newbalanceDest} do CSV, que descrevem
 * a mesma estrutura para a origem e para o destinatário.
 *
 * <p>O nome do tipo é deliberado: os saldos são um retrato do cliente <em>naquela</em>
 * transação, não um atributo permanente dele. O mesmo cliente aparece com saldos
 * diferentes em transações diferentes.
 *
 * @param name       identificador do cliente no dataset (ex.: {@code C1231006815})
 * @param oldBalance saldo antes da transação
 * @param newBalance saldo depois da transação
 */
public record TransactionCustomer(String name, BigDecimal oldBalance, BigDecimal newBalance) {

    public TransactionCustomer {
        Objects.requireNonNull(name, "name não pode ser nulo");
        Objects.requireNonNull(oldBalance, "oldBalance não pode ser nulo");
        Objects.requireNonNull(newBalance, "newBalance não pode ser nulo");

        if (name.isBlank()) {
            throw new IllegalArgumentException("name não pode ser vazio");
        }
    }
}
