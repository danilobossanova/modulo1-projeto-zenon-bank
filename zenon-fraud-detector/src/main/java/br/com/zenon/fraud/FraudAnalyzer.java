package br.com.zenon.fraud;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Análises sobre um conjunto de transações já carregado.
 *
 * <p>A classe não lê arquivos nem conhece o formato do CSV: recebe as transações prontas
 * e apenas as consulta.
 *
 * <p>A lista recebida <em>não</em> é copiada, para não duplicar milhões de referências em
 * memória. Em troca, quem constrói o analisador não deve modificá-la depois.
 */
public class FraudAnalyzer {

    private final List<Transaction> transactions;

    public FraudAnalyzer(List<Transaction> transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions should not be null");
    }

    /**
     * Quantidade de transações marcadas como fraude.
     */
    public long countFrauds() {
        return transactions.stream()
                .filter(Transaction::isFraud)
                .count();
    }

    /**
     * As fraudes de maior valor, da maior para a menor.
     *
     * <p>No PaySim uma fraude costuma aparecer como um par TRANSFER + CASH_OUT de mesmo
     * valor, então valores repetidos no resultado são transações distintas e não duplicatas
     * do dado.
     *
     * <p>A ordenação usa {@link BigDecimal#compareTo}, que compara apenas o valor. Isso
     * importa porque o CSV traz alguns números em notação científica ({@code 1.0E7}), e
     * {@link BigDecimal#equals} consideraria esses valores diferentes de {@code 10000000.00}.
     *
     * @param limit quantas transações devolver
     */
    public List<Transaction> findHighestValueFrauds(long limit) {
        return transactions.stream()
                .filter(Transaction::isFraud)
                .sorted(Comparator.comparing(Transaction::amount).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Nomes dos clientes de origem que mais aparecem em fraudes, sem repetição.
     *
     * @param limit quantos nomes devolver
     */
    public List<String> findTopSuspectOrigins(long limit) {
        return transactions.stream()
                .filter(Transaction::isFraud)
                .sorted(Comparator.comparing(Transaction::amount).reversed())
                .map(transaction -> transaction.origin().name())
                .distinct()
                .limit(limit)
                .toList();
    }

    /**
     * Prejuízo total causado pelas fraudes: a soma dos valores das transações fraudulentas.
     */
    public BigDecimal totalFraudLoss() {

        return transactions.stream()
                .filter(Transaction::isFraud)
                .map(Transaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Quantidade de fraudes em cada tipo de transação.
     */
    public Map<TransactionType, Long> countFraudsByType() {
        return transactions.stream()
                .filter(Transaction::isFraud)
                .collect(Collectors.groupingBy(
                        Transaction::type,
                        ()-> new EnumMap<>(TransactionType.class),
                        Collectors.counting()
                ));
    }
}
