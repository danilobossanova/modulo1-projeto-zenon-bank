package br.com.zenon;

import br.com.zenon.fraud.FraudAnalyzer;
import br.com.zenon.fraud.Transaction;
import br.com.zenon.fraud.TransactionType;
import br.com.zenon.fraud.ingestor.NioTransactionIngestor;
import br.com.zenon.fraud.ingestor.TransactionIngestor;
import br.com.zenon.fraud.repository.TransactionListRepository;
import br.com.zenon.fraud.repository.TransactionMapRepository;
import br.com.zenon.fraud.repository.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Ponto de entrada do projeto: carrega as transações do CSV e executa as entregas das
 * tarefas 5 e 6.
 *
 * <p>Primeiro saem as cinco análises de fraude da tarefa 5. Depois a tarefa 6: a busca de
 * transações por cliente de origem e o benchmark que compara varredura linear com índice
 * de hash.
 *
 * <p>Cada item dos enunciados virou um método de impressão, de modo que {@code main} se leia
 * como o roteiro das tarefas em vez de uma sequência de chamadas e formatações soltas.
 *
 * <p>A tarefa 6 elevou a leitura para 100.000 transações, então os números da seção 5
 * refletem essa amostra maior e não os 50.000 do enunciado daquela tarefa.
 *
 * <p>A divisão de responsabilidades é deliberada: as análises moram em {@link FraudAnalyzer},
 * a busca em {@link TransactionRepository} e a leitura em {@link TransactionIngestor}.
 * Aqui ficam apenas a orquestração e o formato da saída — esta classe não calcula nada.
 */
public class Main {

    /**
     * CSV íntegro do PaySim.
     *
     * <p>Para reexercitar o descarte de linhas corrompidas da tarefa 4, basta apontar para
     * {@code data/paysim_with_bad_data.csv}: o resto do fluxo não muda.
     */
    private static final String ARQUIVO = "data/PS_20174392719_1491204439457_log.csv";

    /** Quantas fraudes de maior valor o item 2 do enunciado da tarefa 5 pede. */
    private static final int TOP_FRAUDES = 3;

    /** Quantos clientes suspeitos o item 3 do enunciado da tarefa 5 pede. */
    private static final int TOP_SUSPEITOS = 5;

    /** Cliente de origem que existe no arquivo: é a primeira transação da lista. */
    private static final String CLIENTE_EXISTENTE = "C1231006815";

    /** Cliente que não existe no arquivo, para exercitar o {@code Optional} vazio. */
    private static final String CLIENTE_INEXISTENTE = "C12345";

    /**
     * Cliente de origem da <em>última</em> transação carregada.
     *
     * <p>É o pior caso da varredura linear: como a busca para no primeiro resultado, procurar
     * o último elemento obriga a avaliar o predicado nas 100.000 transações.
     */
    private static final String CLIENTE_PIOR_CASO = "C1868032458";

    /**
     * Quantas vezes cada busca é repetida na medição.
     *
     * <p>O mesmo número serve para o aquecimento e para o lote medido: primeiro são feitas
     * {@code REPETICOES} buscas descartadas, para o JIT compilar o caminho, e só depois outras
     * {@code REPETICOES} sob o cronômetro. O tempo reportado é o total do lote dividido pelo
     * número de buscas.
     */
    private static final int REPETICOES = 200;

    void main() {

        //A variavel e do tipo da interface: trocar a implementacao abaixo por
        //IoTransactionIngestor ou Nio2TransactionIngestor nao muda mais nada aqui.
        TransactionIngestor ingestor = new NioTransactionIngestor();

        //As linhas invalidas sao registradas em System.err durante a leitura
        List<Transaction> transacoes = ingestor.read(ARQUIVO);

        //Uma leitura so alimenta todas as analises: cada metodo abaixo abre um novo
        //stream sobre a mesma lista, porque um stream nao pode ser reutilizado.
        FraudAnalyzer analyzer = new FraudAnalyzer(transacoes);

        imprimirTotalDeFraudes(analyzer);
        imprimirMaioresFraudes(analyzer);
        imprimirClientesSuspeitos(analyzer);
        imprimirPrejuizoTotal(analyzer);
        imprimirFraudesPorTipo(analyzer);

        //Mesma licao do ingestor: a variavel e do tipo da interface, entao o codigo
        //abaixo nao sabe se a busca varre uma lista ou consulta um mapa.
        //O repositorio usa a lista ja carregada: nao le o arquivo de novo.
        TransactionRepository repository = new TransactionListRepository(transacoes);

        IO.println("\n--- Tarefa 6: busca por cliente de origem ---");
        imprimirBusca(repository, CLIENTE_INEXISTENTE);
        imprimirBusca(repository, CLIENTE_EXISTENTE);

        imprimirBenchmark(transacoes, repository);
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
     * Imprime o resultado de uma busca por cliente de origem.
     *
     * <p>Não há {@code isPresent()} nem {@code get()}: {@code ifPresentOrElse} recebe os dois
     * caminhos e obriga a declarar o que acontece na ausência. Chamar {@code get()} em um
     * {@link java.util.Optional} vazio lança exceção em tempo de execução, o que devolveria
     * ao código o mesmo problema do {@code null} com outro nome.
     *
     * @param repository de onde buscar
     * @param originName nome do cliente de origem procurado
     */
    private void imprimirBusca(TransactionRepository repository, String originName) {
        repository.findByOriginName(originName).ifPresentOrElse(
                IO::println,
                () -> IO.println("Transação não encontrada para o cliente " + originName));
    }

    /**
     * Compara o custo da varredura linear com o do índice de hash.
     *
     * <p>A busca é pelo cliente da última transação carregada, o pior caso da lista. Os dois
     * repositórios devolvem a mesma transação; o que se mede é quanto cada estrutura cobra
     * para chegar nela.
     *
     * <p>Além do tempo de consulta, a medição informa o custo de montar o índice e o ponto
     * de equilíbrio. Sem esses dois números a comparação seria enganosa: o {@code Map} é mais
     * rápido por consulta, mas cobra O(n) adiantado, então para uma busca única a lista ganha.
     *
     * @param transacoes     a lista carregada, usada para montar o índice
     * @param listRepository o repositório de varredura linear, já construído
     */
    private void imprimirBenchmark(List<Transaction> transacoes, TransactionRepository listRepository) {
        IO.println("\n--- Tarefa 6: benchmark de busca (pior caso, " + CLIENTE_PIOR_CASO + ") ---");

        long nsLista = medirTempoMedio(listRepository, CLIENTE_PIOR_CASO);
        IO.println("List, varredura O(n): " + formatarDuracao(nsLista));

        long inicioIndice = System.nanoTime();
        TransactionRepository mapRepository = new TransactionMapRepository(transacoes);
        long nsIndice = System.nanoTime() - inicioIndice;

        long nsMapa = medirTempoMedio(mapRepository, CLIENTE_PIOR_CASO);
        IO.println("Map, indice O(1):     " + formatarDuracao(nsMapa));
        IO.println("Consulta " + (nsLista / nsMapa) + "x mais rapida no Map");

        IO.println("\nCusto de montar o indice, O(n) pago uma vez: " + formatarDuracao(nsIndice));
        double equilibrio = (double) nsIndice / (nsLista - nsMapa);
        IO.println("Ponto de equilibrio: o indice se paga a partir de "
                + String.format(Locale.ROOT, "%.0f", equilibrio) + " buscas");
    }

    /**
     * Mede o custo médio de uma busca, em nanossegundos.
     *
     * <p>A medição cerca um <em>lote</em> de buscas e divide pelo número delas, em vez de
     * cronometrar uma chamada isolada. O motivo é a resolução do relógio: no Windows o
     * {@link System#nanoTime()} avança em passos da ordem de 100 ns, e a consulta ao índice
     * de hash custa menos do que isso. Cronometrada sozinha, ela reportaria um ou dois ticks
     * de granularidade do cronômetro em vez do seu custo real. Dividir o lote enxerga abaixo
     * da resolução do relógio.
     *
     * <p>O laço de aquecimento que vem antes existe porque as primeiras execuções rodam
     * interpretadas, antes de o JIT compilar o caminho. Sem ele, a média carregaria o custo
     * do interpretador e não o do código compilado — e carregaria de forma desigual entre as
     * duas implementações, já que cada uma aquece em ritmo diferente.
     *
     * @param repository o repositório a medir
     * @param originName o cliente a procurar, que precisa existir
     * @return o custo médio por busca
     * @throws IllegalStateException se o cliente não for encontrado
     */
    private long medirTempoMedio(TransactionRepository repository, String originName) {
        for (int i = 0; i < REPETICOES; i++) {
            consumir(repository.findByOriginName(originName), originName);
        }

        long inicio = System.nanoTime();
        for (int i = 0; i < REPETICOES; i++) {
            consumir(repository.findByOriginName(originName), originName);
        }
        return (System.nanoTime() - inicio) / REPETICOES;
    }

    /**
     * Valida e consome o resultado de uma busca do benchmark.
     *
     * <p>Consumir é necessário: se o resultado fosse descartado, o compilador poderia concluir
     * que a chamada não tem efeito observável e eliminá-la, caso em que o benchmark mediria
     * um laço vazio. A validação, de quebra, garante que estamos medindo uma busca que
     * encontra algo — uma busca sem resultado percorreria a lista inteira e mediria outro caso.
     */
    private void consumir(Optional<Transaction> encontrada, String originName) {
        if (encontrada.isEmpty()) {
            throw new IllegalStateException("cliente ausente no benchmark: " + originName);
        }
    }

    /**
     * Formata uma duração em nanossegundos e no equivalente em milissegundos.
     *
     * <p>Os dois são necessários porque as escalas comparadas diferem em ordens de grandeza:
     * a consulta ao índice cai na casa das dezenas de nanossegundos, onde milissegundos
     * arredondariam para zero.
     */
    private String formatarDuracao(long nanos) {
        return String.format(Locale.ROOT, "%,d ns (%.3f ms)", nanos, nanos / 1_000_000.0);
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
