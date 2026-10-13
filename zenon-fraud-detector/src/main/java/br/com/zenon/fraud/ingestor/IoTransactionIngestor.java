package br.com.zenon.fraud.ingestor;

import br.com.zenon.fraud.Transaction;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Leitura com a API clássica {@code java.io}, de 1996 ({@link FileInputStream} + {@link Scanner}).
 *
 * <p>A abstração central do {@code java.io} é o <em>stream</em>: uma sequência de bytes que
 * se consome do começo ao fim, sem voltar atrás. O {@link Scanner} cuida de agrupar esses
 * bytes em linhas, então esta é a implementação mais curta das três.
 *
 * <p>Em compensação é a mais lenta: o {@link Scanner} usa expressões regulares internamente
 * para delimitar os tokens, o que pesa quando o arquivo tem milhões de linhas.
 */
public class IoTransactionIngestor implements TransactionIngestor {

    @Override
    public List<Transaction> read(String fileName) {

        List<Transaction> transactions = new ArrayList<>();
        int lineCounter = 0;

        try (FileInputStream fis = new FileInputStream(fileName);
             Scanner sc = new Scanner(fis, StandardCharsets.UTF_8)) {

            while (lineCounter < LAST_LINE_TO_READ && sc.hasNextLine()) {

                String line = sc.nextLine();
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
