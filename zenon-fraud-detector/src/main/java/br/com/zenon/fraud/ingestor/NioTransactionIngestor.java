package br.com.zenon.fraud.ingestor;

import br.com.zenon.fraud.Transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Leitura com a API {@code java.nio}, de 2002 ({@link FileChannel} + {@link ByteBuffer}).
 *
 * <p>Aqui não existe o conceito de "linha": o canal entrega <em>blocos de bytes</em> de
 * tamanho fixo. Montar as linhas é trabalho nosso, e é por isso que esta é a implementação
 * mais longa das três — o que ilustra exatamente o nível de abstração dessa API.
 *
 * <p>O detalhe que torna isso não-trivial é que uma linha quase sempre começa num bloco e
 * termina no seguinte. Por isso os bytes vão se acumulando em {@code currentLine} e só viram
 * texto quando a quebra de linha aparece.
 *
 * <p>Procuramos o byte {@code '\n'} em vez de decodificar cada bloco para texto porque um
 * bloco pode cortar um caractere UTF-8 multibyte ao meio. Buscar pelo byte é seguro: em UTF-8
 * os bytes de continuação são todos maiores que 0x7F, então {@code '\n'} nunca aparece dentro
 * de um caractere — apenas como separador de verdade.
 */
public class NioTransactionIngestor implements TransactionIngestor {

    /** Tamanho do bloco lido do canal a cada vez. */
    private static final int BUFFER_SIZE = 8 * 1024;

    private static final byte LINE_FEED = '\n';

    @Override
    public List<Transaction> read(String fileName) {

        List<Transaction> transactions = new ArrayList<>();
        ByteArrayOutputStream currentLine = new ByteArrayOutputStream();
        int lineCounter = 0;

        try (FileChannel channel = FileChannel.open(Path.of(fileName), StandardOpenOption.READ)) {

            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

            while (lineCounter < LAST_LINE_TO_READ && channel.read(buffer) != -1) {

                buffer.flip();   //do modo escrita para o modo leitura

                while (buffer.hasRemaining() && lineCounter < LAST_LINE_TO_READ) {

                    byte b = buffer.get();

                    if (b == LINE_FEED) {
                        lineCounter++;
                        accept(currentLine, lineCounter, transactions);
                        currentLine.reset();
                    } else {
                        currentLine.write(b);
                    }
                }

                buffer.clear();  //volta para o modo escrita
            }

            //O arquivo pode terminar sem quebra de linha na última linha
            if (currentLine.size() > 0 && lineCounter < LAST_LINE_TO_READ) {
                lineCounter++;
                accept(currentLine, lineCounter, transactions);
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o arquivo " + fileName, e);
        }

        return transactions;
    }

    /**
     * Converte os bytes acumulados em texto e acrescenta a transação resultante.
     *
     * <p>Remove um {@code '\r'} final para que arquivos com quebra de linha do Windows
     * (CRLF) produzam o mesmo resultado das outras duas implementações.
     */
    private static void accept(ByteArrayOutputStream line, int lineNumber, List<Transaction> out) {

        //A primeira linha é o cabeçalho, não é dado
        if (lineNumber == 1) {
            return;
        }

        String text = line.toString(StandardCharsets.UTF_8);
        if (text.endsWith("\r")) {
            text = text.substring(0, text.length() - 1);
        }

        TransactionLineParser.tryParse(text).ifPresent(out::add);
    }
}
