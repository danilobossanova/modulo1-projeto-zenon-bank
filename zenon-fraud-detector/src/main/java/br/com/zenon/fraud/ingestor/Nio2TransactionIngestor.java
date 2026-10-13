package br.com.zenon.fraud.ingestor;

import br.com.zenon.fraud.Transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Leitura com a NIO.2, de 2011 ({@link Files} + {@link Path}).
 *
 * <p>A NIO.2 não substituiu a {@code java.nio}: ela acrescentou uma API de sistema de
 * arquivos. {@link Path} representa o caminho de forma independente de plataforma e
 * {@link Files} concentra as operações, incluindo atalhos de alto nível como este
 * {@link Files#newBufferedReader(Path, java.nio.charset.Charset)}.
 *
 * <p>É a implementação mais direta das três: o charset é um parâmetro em vez de um detalhe
 * implícito, e o caminho deixa de ser uma {@code String} solta.
 *
 * <p>{@link Files#lines(Path)} daria uma versão ainda mais curta, com {@code Stream}. Optamos
 * pelo laço porque assim esta implementação numera as linhas igual às outras duas, e as três
 * falham com a mesma mensagem — o que é o ponto de poder trocar uma pela outra.
 */
public class Nio2TransactionIngestor implements TransactionIngestor {

    @Override
    public List<Transaction> read(String fileName) {

        List<Transaction> transactions = new ArrayList<>();
        int lineCounter = 0;

        try (BufferedReader reader = Files.newBufferedReader(Path.of(fileName), StandardCharsets.UTF_8)) {

            String line;
            while (lineCounter < LAST_LINE_TO_READ && (line = reader.readLine()) != null) {

                lineCounter++;

                //A primeira linha é o cabeçalho, não é dado
                if (lineCounter == 1) {
                    continue;
                }

                TransactionLineParser.tryParse(line).ifPresent(transactions::add);
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o arquivo " + fileName, e);
        }

        return transactions;
    }
}
