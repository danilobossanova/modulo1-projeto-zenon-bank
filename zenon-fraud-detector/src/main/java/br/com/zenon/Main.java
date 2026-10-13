package br.com.zenon;

import br.com.zenon.fraud.Transaction;
import br.com.zenon.fraud.ingestor.IoTransactionIngestor;
import br.com.zenon.fraud.ingestor.TransactionIngestor;

import java.util.List;

/**
 * Ponto de entrada da Tarefa 4: lê um CSV com linhas corrompidas, descartando as inválidas
 * sem abortar a leitura, e imprime a quantidade e as transações aproveitadas.
 */
public class Main {

    private static final String ARQUIVO = "data/paysim_with_bad_data.csv";

    void main() {

        //A variavel e do tipo da interface: trocar a implementacao abaixo por
        //NioTransactionIngestor ou Nio2TransactionIngestor nao muda mais nada aqui.
        TransactionIngestor ingestor = new IoTransactionIngestor();

        //As linhas invalidas sao registradas em System.err durante a leitura
        List<Transaction> transacoes = ingestor.read(ARQUIVO);

        IO.println(transacoes.size());
        transacoes.forEach(IO::println);
    }
}
