package br.com.zenon.fraud;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Transação financeira do dataset PaySim.
 *
 * <p>As seis colunas de cliente do CSV estão agrupadas em {@link TransactionCustomer}: as
 * colunas {@code nameOrig}/{@code oldbalanceOrg}/{@code newbalanceOrig} viram {@code origin}
 * e {@code nameDest}/{@code oldbalanceDest}/{@code newbalanceDest} viram {@code recipient}.
 * Isso elimina a duplicação da mesma estrutura e torna impossível trocar por engano um
 * saldo de origem por um de destino.
 *
 * <p>Valores monetários usam {@link BigDecimal} para evitar os erros de arredondamento
 * de ponto flutuante binário. Ao criar instâncias, prefira sempre o construtor que recebe
 * {@code String} ({@code new BigDecimal("9839.64")}): o que recebe {@code double} carrega
 * o ruído da representação binária para dentro do valor.
 *
 * @param step           hora simulada da transação no dataset, começando em 1
 * @param type           tipo da operação
 * @param amount         valor transacionado
 * @param origin         cliente de origem, com seus saldos antes e depois
 * @param recipient      cliente destinatário, com seus saldos antes e depois
 * @param isFraud        se a transação é efetivamente fraudulenta
 * @param isFlaggedFraud se a transação foi sinalizada como suspeita pelo sistema
 */
public record Transaction(
        int step,
        TransactionType type,
        BigDecimal amount,
        TransactionCustomer origin,
        TransactionCustomer recipient,
        boolean isFraud,
        boolean isFlaggedFraud
) {

    public Transaction {
        Objects.requireNonNull(type, "type should not be null");
        Objects.requireNonNull(amount, "amount should not be null");
        Objects.requireNonNull(origin, "origin should not be null");
        Objects.requireNonNull(recipient, "recipient should not be null");

        if (step < 1) {
            throw new IllegalArgumentException("step should be positive: " + step);
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount should be positive: " + amount);
        }

        /*
         * Intencionalmente NÃO validamos a coerência entre saldos e valor
         * (newBalance == oldBalance - amount). Essa inconsistência ocorre com
         * frequência no PaySim e é parte do sinal usado para detectar fraude;
         * transformá-la em exceção impediria a leitura do dataset.
         */
    }
}
