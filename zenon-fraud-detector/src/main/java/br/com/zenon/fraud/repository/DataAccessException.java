package br.com.zenon.fraud.repository;

/**
 * Falha ao acessar o armazenamento de um repositório.
 *
 * <p>Existe para converter a {@link java.sql.SQLException}, que é checada, em uma exceção não
 * checada. É o que permite a {@link TransactionRepository} declarar suas operações sem
 * mencionar JDBC: sem essa conversão, o contrato obrigaria as implementações em memória a
 * tratar uma exceção que elas nunca podem lançar.
 *
 * <p>A causa original é sempre preservada. A mensagem do servidor, o {@code SQLState} e o
 * código do fornecedor continuam acessíveis por {@link #getCause()}, e é ali que está a
 * informação que serve para diagnosticar a falha — a mensagem desta exceção diz apenas qual
 * operação falhou.
 */
public class DataAccessException extends RuntimeException {

    /**
     * Envolve a falha original, preservando-a como causa.
     *
     * @param message qual operação falhou
     * @param cause   a exceção original do driver, nunca descartada
     */
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
