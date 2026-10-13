package br.com.zenon;

import br.com.zenon.fraud.Transaction;
import br.com.zenon.fraud.TransactionCustomer;
import br.com.zenon.fraud.TransactionType;
import br.com.zenon.fraud.ingestor.IoTransactionIngestor;
import br.com.zenon.fraud.ingestor.TransactionIngestor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ponto de entrada da Tarefa 2: monta transações de exemplo e as imprime no console.
 */
public class Main {

    void main() {

        var pagamento = new Transaction(1, TransactionType.PAYMENT, new BigDecimal("9839.64"),
                new TransactionCustomer("C1231006815", new BigDecimal("170136.0"), new BigDecimal("160296.36")),
                new TransactionCustomer("M1979787155", new BigDecimal("0.0"), new BigDecimal("0.0")),
                false, false
        );

        var saque = new Transaction(743, TransactionType.CASH_OUT, new BigDecimal("850002.52"),
                new TransactionCustomer("C1280323807", new BigDecimal("850002.52"), new BigDecimal("0.0")),
                new TransactionCustomer("C873221189", new BigDecimal("6510099.11"), new BigDecimal("7360101.63")),
                true, false
        );

        for (var transacao : List.of(pagamento, saque)) {
            IO.println(transacao);
        }

        IO.println("\n-------------------------------------------------------");

        //A variavel e do tipo da interface: trocar a implementacao abaixo por
        //NioTransactionIngestor ou Nio2TransactionIngestor nao muda mais nada aqui.
        TransactionIngestor ingestor = new IoTransactionIngestor();

        List<Transaction> transacoes = ingestor.read("data/PS_20174392719_1491204439457_log.csv");
        transacoes.stream().limit(10).forEach(IO::println);

    }
}
