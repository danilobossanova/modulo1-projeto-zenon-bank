package br.com.zenon.fraud.report;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resultado agregado da varredura de um arquivo de transações.
 *
 * <p>São três números, e nada mais. É isso que permite processar um arquivo de centenas de
 * megabytes com pouca memória: em vez de guardar as 6,3 milhões de transações para depois
 * consultá-las, cada linha é lida, contabilizada e descartada, restando apenas este resumo.
 *
 * @param totalLines  quantidade de linhas de dados lidas, sem contar o cabeçalho
 * @param totalFrauds quantas dessas linhas estavam marcadas como fraude
 * @param totalAmount soma dos valores transacionados em todas as linhas
 */
public record TransactionSummary(long totalLines, long totalFrauds, BigDecimal totalAmount) {

    /**
     * Valida a coerência dos totais.
     *
     * <p>A regra de que não pode haver mais fraudes do que linhas não é decorativa: ela pega
     * um erro de contagem no acumulador, onde os dois contadores são incrementados em pontos
     * diferentes do mesmo método.
     *
     * @throws NullPointerException     se o valor total for nulo
     * @throws IllegalArgumentException se algum total for negativo, ou se houver mais fraudes
     *                                  do que linhas
     */
    public TransactionSummary {
        Objects.requireNonNull(totalAmount, "totalAmount should not be null");

        if (totalLines < 0) {
            throw new IllegalArgumentException("totalLines should not be negative: " + totalLines);
        }
        if (totalFrauds < 0) {
            throw new IllegalArgumentException("totalFrauds should not be negative: " + totalFrauds);
        }
        if (totalFrauds > totalLines) {
            throw new IllegalArgumentException(
                    "totalFrauds (" + totalFrauds + ") should not exceed totalLines (" + totalLines + ")");
        }
    }
}
