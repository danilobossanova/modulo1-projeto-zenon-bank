package br.com.zenon.fraud;

/**
 * Tipo de operação de uma transação, conforme a coluna {@code type} do CSV do PaySim.
 *
 * <p>Os nomes das constantes são idênticos aos valores encontrados no arquivo, o que
 * permite converter a coluna com {@link #valueOf(String)} sem tabela de mapeamento.
 */
public enum TransactionType {

    /** Entrada de dinheiro em espécie na conta. */
    CASH_IN,

    /** Saque de dinheiro em espécie da conta. */
    CASH_OUT,

    /** Débito em conta. */
    DEBIT,

    /** Pagamento a um estabelecimento. */
    PAYMENT,

    /** Transferência entre contas. */
    TRANSFER
}
