package br.com.zenon.fraud.ingestor;

import br.com.zenon.fraud.Transaction;
import br.com.zenon.fraud.TransactionCustomer;
import br.com.zenon.fraud.TransactionType;

import java.math.BigDecimal;

/**
 * Converte uma linha do CSV do PaySim em uma {@link Transaction}.
 *
 * <p>É o único ponto do projeto que conhece o formato do arquivo. As implementações de
 * {@link TransactionIngestor} diferem apenas em <em>como obtêm</em> as linhas; o que elas
 * fazem com cada linha é esta classe — por isso ela não é duplicada nas três.
 *
 * <p>O arquivo não usa aspas nem vírgulas dentro dos campos, então {@link String#split(String)}
 * é suficiente e dispensa uma biblioteca de CSV.
 */
final class TransactionLineParser {

    /** Quantidade de colunas que o CSV do PaySim deve ter. */
    private static final int EXPECTED_COLUMNS = 11;

    // Posição de cada coluna na linha do CSV
    private static final int COL_STEP = 0;
    private static final int COL_TYPE = 1;
    private static final int COL_AMOUNT = 2;
    private static final int COL_NAME_ORIG = 3;
    private static final int COL_OLD_BALANCE_ORIG = 4;
    private static final int COL_NEW_BALANCE_ORIG = 5;
    private static final int COL_NAME_DEST = 6;
    private static final int COL_OLD_BALANCE_DEST = 7;
    private static final int COL_NEW_BALANCE_DEST = 8;
    private static final int COL_IS_FRAUD = 9;
    private static final int COL_IS_FLAGGED_FRAUD = 10;

    private TransactionLineParser() {
        //Classe utilitaria, nao deve ser instanciada
    }

    /**
     * Converte uma linha de dados, identificando a linha na mensagem em caso de falha.
     *
     * <p>Saber <em>qual</em> linha quebrou é a única pergunta que importa num arquivo com
     * milhões de registros, por isso o número e o conteúdo entram na mensagem.
     *
     * @param line       a linha, já sem a quebra de linha
     * @param lineNumber posição da linha no arquivo, contando o cabeçalho
     */
    static Transaction parseDataLine(String line, int lineNumber) {
        try {
            return parse(line);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Falha ao processar a linha " + lineNumber + ": " + line, e);
        }
    }

    /**
     * Converte uma linha de dados em uma transação.
     *
     * <p>As colunas 3 a 5 descrevem o cliente de origem e as colunas 6 a 8 o destinatário,
     * o que corresponde diretamente aos dois {@link TransactionCustomer} da transação.
     */
    static Transaction parse(String line) {

        String[] chunks = line.split(",");

        if (chunks.length != EXPECTED_COLUMNS) {
            throw new IllegalArgumentException(
                    "esperadas " + EXPECTED_COLUMNS + " colunas, encontradas " + chunks.length);
        }

        TransactionCustomer origin = new TransactionCustomer(
                chunks[COL_NAME_ORIG],
                new BigDecimal(chunks[COL_OLD_BALANCE_ORIG]),
                new BigDecimal(chunks[COL_NEW_BALANCE_ORIG]));

        TransactionCustomer recipient = new TransactionCustomer(
                chunks[COL_NAME_DEST],
                new BigDecimal(chunks[COL_OLD_BALANCE_DEST]),
                new BigDecimal(chunks[COL_NEW_BALANCE_DEST]));

        return new Transaction(
                Integer.parseInt(chunks[COL_STEP]),
                TransactionType.valueOf(chunks[COL_TYPE]),
                new BigDecimal(chunks[COL_AMOUNT]),
                origin,
                recipient,
                isTrue(chunks[COL_IS_FRAUD]),
                isTrue(chunks[COL_IS_FLAGGED_FRAUD]));
    }

    /**
     * O CSV representa booleanos como 0 e 1.
     *
     * <p>{@link Boolean#parseBoolean(String)} não serve aqui: ele só reconhece a palavra
     * {@code "true"} e devolveria {@code false} para {@code "1"}.
     */
    private static boolean isTrue(String value) {
        return "1".equals(value);
    }
}
