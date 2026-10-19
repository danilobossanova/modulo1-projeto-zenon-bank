package br.com.zenon.fraud.ingestor;

import br.com.zenon.fraud.Transaction;

import java.io.UncheckedIOException;
import java.util.List;

/**
 * Contrato de leitura de transações a partir do CSV do PaySim.
 *
 * <p>Existe uma implementação para cada geração de API de arquivos do Java:
 *
 * <ul>
 *   <li>{@link IoTransactionIngestor} — {@code java.io} (Java 1.0): streams bloqueantes</li>
 *   <li>{@link NioTransactionIngestor} — {@code java.nio} (Java 1.4): canais e buffers</li>
 *   <li>{@link Nio2TransactionIngestor} — {@code java.nio.file} (Java 7): a NIO.2</li>
 * </ul>
 *
 * <p>As três devolvem exatamente o mesmo resultado para o mesmo arquivo e falham da mesma
 * forma. O que muda é apenas como os bytes saem do disco — que é justamente o ponto da
 * comparação.
 */
public interface TransactionIngestor {

    /**
     * Última linha do arquivo a ser lida, contando o cabeçalho.
     *
     * <p>Limite temporário, declarado aqui para que as três implementações leiam a mesma
     * fatia do arquivo e seus resultados sejam comparáveis.
     */
    int LAST_LINE_TO_READ = 100_001;

    /**
     * Lê o arquivo e devolve as transações encontradas, na ordem em que aparecem.
     *
     * @param fileName caminho do CSV, relativo ao diretório de trabalho
     * @return as transações lidas
     * @throws UncheckedIOException     se o arquivo não puder ser aberto ou lido
     * @throws IllegalArgumentException se alguma linha estiver malformada
     */
    List<Transaction> read(String fileName);
}
