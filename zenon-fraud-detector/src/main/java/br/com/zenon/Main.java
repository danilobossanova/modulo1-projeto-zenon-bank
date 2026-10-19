package br.com.zenon;

import br.com.zenon.fraud.FraudAnalyzer;
import br.com.zenon.fraud.Transaction;
import br.com.zenon.fraud.TransactionType;
import br.com.zenon.fraud.ingestor.NioTransactionIngestor;
import br.com.zenon.fraud.ingestor.TransactionIngestor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Ponto de entrada da Tarefa 5: carrega as transações do CSV e imprime as cinco análises
 * de fraude pedidas pelo enunciado.
 *
 * <p>Cada item do enunciado virou um método de impressão, de modo que {@code main} se leia
 * como o roteiro da tarefa em vez de uma sequência de chamadas e formatações soltas.
 *
 * <p>A divisão de responsabilidades é deliberada: as análises moram em {@link FraudAnalyzer}
 * e a leitura em {@link TransactionIngestor}. Aqui ficam apenas a orquestração e o formato
 * da saída — esta classe não calcula nada.
 */
public class Main {

    /**
     * CSV íntegro do PaySim.
     *
     * <p>Para reexercitar o descarte de linhas corrompidas da tarefa 4, basta apontar para
     * {@code data/paysim_with_bad_data.csv}: o resto do fluxo não muda.
     */
    private static final String ARQUIVO = "data/PS_20174392719_1491204439457_log.csv";

    /** Quantas fraudes de maior valor o item 2 do enunciado pede. */
    private static final int TOP_FRAUDES = 3;

    /** Quantos clientes suspeitos o item 3 do enunciado pede. */
    private static final int TOP_SUSPEITOS = 5;

    void main() {

        //A variavel e do tipo da interface: trocar a implementacao abaixo por
        //IoTransactionIngestor ou Nio2TransactionIngestor nao muda mais nada aqui.
        TransactionIngestor ingestor = new NioTransactionIngestor();

        //As linhas invalidas sao registradas em System.err durante a leitura
        List<Transaction> transacoes = ingestor.read(ARQUIVO);

        //Uma leitura so alimenta as cinco analises: cada metodo abaixo abre um novo
        //stream sobre a mesma lista, porque um stream nao pode ser reutilizado.
        FraudAnalyzer analyzer = new FraudAnalyzer(transacoes);

        imprimirTotalDeFraudes(analyzer);
        imprimirMaioresFraudes(analyzer);
        imprimirClientesSuspeitos(analyzer);
        imprimirPrejuizoTotal(analyzer);
        imprimirFraudesPorTipo(analyzer);
    }

    /** Item 1: quantas transações do arquivo estão marcadas como fraude. */
    private void imprimirTotalDeFraudes(FraudAnalyzer analyzer) {
        IO.println("1. Total de Fraudes: " + analyzer.countFrauds());
    }

    /**
     * Item 2: as fraudes de maior valor, da maior para a menor.
     *
     * <p>Valores repetidos na saída não são duplicatas do dado: no PaySim uma fraude costuma
     * aparecer como um par TRANSFER + CASH_OUT de mesmo valor.
     */
    private void imprimirMaioresFraudes(FraudAnalyzer analyzer) {
        IO.println("2. Top " + TOP_FRAUDES + " Fraudes de Maior Valor:");
        analyzer.findHighestValueFrauds(TOP_FRAUDES).stream()
                .map(Transaction::amount)
                .map(this::formatarValor)
                .forEach(IO::println);
    }

    /** Item 3: os clientes de origem que respondem pelas maiores fraudes, sem repetição. */
    private void imprimirClientesSuspeitos(FraudAnalyzer analyzer) {
        IO.println("3. Clientes Suspeitos:");
        analyzer.findTopSuspectOrigins(TOP_SUSPEITOS).forEach(IO::println);
    }

    /** Item 4: a soma dos valores de todas as transações fraudulentas. */
    private void imprimirPrejuizoTotal(FraudAnalyzer analyzer) {
        IO.println("4. Prejuízo Total: " + formatarValor(analyzer.totalFraudLoss()));
    }

    /**
     * Item 5: quantas fraudes ocorreram em cada tipo de transação.
     *
     * <p>Só aparecem os tipos que têm ao menos uma fraude: o {@code groupingBy} não cria
     * chave para grupo vazio, então tipos sem fraude ficam fora do mapa em vez de sair
     * com zero.
     */
    private void imprimirFraudesPorTipo(FraudAnalyzer analyzer) {
        IO.println("5. Fraudes por Tipo:");
        Map<TransactionType, Long> fraudesPorTipo = analyzer.countFraudsByType();
        fraudesPorTipo.forEach((tipo, quantidade) -> IO.println(" - " + tipo + ": " + quantidade));
    }

    /**
     * Formata um valor monetário para a saída.
     *
     * <p>{@code toPlainString} porque o CSV traz alguns valores em notação científica
     * ({@code 1.0E7}) e o {@code toString} do {@link BigDecimal} manteria esse formato.
     * {@code setScale(2)} porque são justamente esses valores que chegam sem casas
     * decimais, e o enunciado espera duas em toda a saída.
     */
    private String formatarValor(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
