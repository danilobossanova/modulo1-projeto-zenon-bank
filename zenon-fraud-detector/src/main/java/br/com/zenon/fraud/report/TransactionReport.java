package br.com.zenon.fraud.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Relatório agregado sobre um arquivo de transações, calculado sem carregá-lo na memória.
 *
 * <p>A diferença em relação a {@code TransactionIngestor} não é a API de I/O, é o que se faz
 * com as linhas. O ingestor devolve uma {@code List<Transaction>}, e por isso precisa manter
 * todas as transações vivas ao mesmo tempo — com o arquivo completo do PaySim, cerca de
 * 493 MB de texto, isso estoura um heap de 128 MB. Aqui cada linha é lida, somada ao
 * acumulador e descartada; o que sobrevive à varredura são três números.
 *
 * <p>{@link Files#lines(Path)} devolve um {@code Stream<String>} <em>preguiçoso</em>: ele não
 * lê o arquivo para montar o stream, lê conforme o consumidor pede a próxima linha. O arquivo
 * nunca está inteiro na memória, apenas o buffer de leitura e a linha corrente.
 *
 * <p>Vale separar duas coisas que o {@code OutOfMemoryError} desta tarefa costuma confundir: o
 * problema era a <em>retenção</em>, não a criação de objetos. Criar e jogar fora milhões de
 * objetos de vida curta é algo que o coletor de lixo faz bem; o que não cabe no heap é
 * mantê-los todos referenciados.
 */
public class TransactionReport {

    /** Posição da coluna {@code amount} na linha do CSV. */
    private static final int COL_AMOUNT = 2;

    /** Posição da coluna {@code isFraud} na linha do CSV. */
    private static final int COL_IS_FRAUD = 9;

    /** O CSV representa o booleano de fraude como 0 ou 1. */
    private static final String FRAUD_FLAG = "1";

    /**
     * Cria o relatório.
     *
     * <p>A classe não guarda estado: o arquivo é informado em cada chamada de
     * {@link #summarize(String)}, como em {@code TransactionIngestor.read}. Guardar o caminho
     * em um campo daria a impressão de que o resumo é calculado uma vez e reaproveitado, o que
     * não é o caso — cada chamada varre o arquivo de novo.
     */
    public TransactionReport() {
    }

    /**
     * Varre o arquivo e devolve os totais.
     *
     * <p>O {@code try}-com-recursos não é opcional: o stream de {@link Files#lines(Path)} mantém
     * um descritor de arquivo aberto, e o fechamento do stream é o que o libera. Um stream não
     * fechado vaza o descritor até o fim do processo.
     *
     * <p>O {@code skip(1)} descarta o cabeçalho, de modo que {@code totalLines} conta linhas de
     * dados. Ele continua sendo preguiçoso: apenas ignora o primeiro elemento, sem materializar
     * o resto.
     *
     * @param fileName caminho do CSV, relativo ao diretório de trabalho
     * @return os totais de linhas, de fraudes e de valor transacionado
     * @throws UncheckedIOException se o arquivo não puder ser aberto ou lido
     */
    public TransactionSummary summarize(String fileName) {
        Path path = Path.of(fileName);

        try (Stream<String> lines = Files.lines(path)) {
            return lines.skip(1)
                    .collect(Accumulator::new, Accumulator::accept, Accumulator::combine)
                    .toSummary();
        } catch (IOException e) {
            throw new UncheckedIOException("não foi possível ler " + fileName, e);
        }
    }

    /**
     * Acumulador mutável dos três totais.
     *
     * <p>É a forma de redução mutável prevista pela Stream API: a sobrecarga de três argumentos
     * de {@code collect} recebe um fornecedor, um acumulador e um combinador. A alternativa
     * seria um {@code reduce} sobre um valor imutável, que criaria um objeto novo por linha —
     * 6,3 milhões deles, sem necessidade.
     *
     * <p>Preferir essa forma a um {@code forEach} sobre uma variável externa não é preciosismo:
     * aqui o estado mutável pertence à redução, e não ao escopo que a chama.
     */
    private static final class Accumulator {

        private long lines;
        private long frauds;
        private BigDecimal amount = BigDecimal.ZERO;

        /**
         * Contabiliza uma linha de dados.
         *
         * <p>São lidas apenas as duas colunas que o relatório usa. Montar uma
         * {@code Transaction} completa por linha funcionaria em memória, já que o objeto morreria
         * em seguida, mas desperdiçaria trabalho: o relatório não tem o que fazer com os saldos,
         * os nomes dos clientes ou o tipo da operação.
         */
        void accept(String line) {
            String[] chunks = line.split(",");

            lines++;
            if (FRAUD_FLAG.equals(chunks[COL_IS_FRAUD])) {
                frauds++;
            }
            amount = amount.add(new BigDecimal(chunks[COL_AMOUNT]));
        }

        /**
         * Funde dois acumuladores parciais.
         *
         * <p>Exigido pelo contrato do {@code collect} de três argumentos e usado somente em
         * stream paralelo. A varredura aqui é sequencial, mas um combinador correto é o que
         * mantém a redução válida caso alguém adicione {@code parallel()} à cadeia.
         */
        void combine(Accumulator outro) {
            lines += outro.lines;
            frauds += outro.frauds;
            amount = amount.add(outro.amount);
        }

        TransactionSummary toSummary() {
            return new TransactionSummary(lines, frauds, amount);
        }
    }
}
