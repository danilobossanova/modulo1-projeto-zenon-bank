package br.com.zenon;

import br.com.zenon.fraud.Transaction;
import br.com.zenon.fraud.ingestor.NioTransactionIngestor;
import br.com.zenon.fraud.ingestor.TransactionIngestor;
import br.com.zenon.fraud.repository.DataAccessException;
import br.com.zenon.fraud.repository.TransactionRepository;
import br.com.zenon.fraud.repository.TransactionSQLRepository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Ponto de entrada da Tarefa 9: lê o CSV do PaySim, grava as transações no MySQL, mede o
 * tempo de inserção e consulta o banco.
 *
 * <p>Requer o MySQL em pé. Da raiz do repositório:
 *
 * <pre>{@code docker compose up -d}</pre>
 *
 * <p>A medição compara dois caminhos para a mesma carga. É o que dá sentido à pergunta da
 * tarefa: inserir 10.000 linhas é rápido ou lento? A resposta só existe em relação a algo, e o
 * algo aqui é a mesma inserção feita linha a linha.
 */
public class DBMain {

    /** CSV do PaySim. O ingestor lê apenas as primeiras 10.000 transações. */
    private static final String ARQUIVO = "data/PS_20174392719_1491204439457_log.csv";

    /**
     * URL do MySQL local subido pelo {@code docker-compose.yml}.
     *
     * <p>O parâmetro {@code rewriteBatchedStatements=true} é o que faz o lote valer a pena: sem
     * ele o driver aceita o {@code addBatch} e ainda assim envia uma instrução por vez, de modo
     * que {@code executeBatch} fica quase tão lento quanto o laço. É uma configuração de
     * conexão, não de código — o mesmo programa muda de desempenho conforme a URL.
     *
     * <p>{@code sslMode=DISABLED} e {@code allowPublicKeyRetrieval=true} valem para este banco
     * local de desenvolvimento. Em um servidor real, o lugar deles é o oposto: TLS ligado.
     */
    private static final String URL_PADRAO = "jdbc:mysql://localhost:3306/zenon"
            + "?rewriteBatchedStatements=true&sslMode=DISABLED&allowPublicKeyRetrieval=true";

    /** Credenciais de desenvolvimento, iguais às do {@code docker-compose.yml}. */
    private static final String USUARIO_PADRAO = "zenon";
    private static final String SENHA_PADRAO = "zenon-dev";

    /** Cliente de origem que existe no arquivo: é a primeira transação lida. */
    private static final String CLIENTE_EXISTENTE = "C1231006815";

    /** Cliente que não existe, para exercitar o {@code Optional} vazio vindo do banco. */
    private static final String CLIENTE_INEXISTENTE = "C12345";

    /**
     * Quantas linhas inserir uma a uma na medição de comparação.
     *
     * <p>Menos que a carga completa de propósito. O custo por linha desse caminho é
     * praticamente constante, então repeti-lo 10.000 vezes consumiria minutos sem acrescentar
     * informação — a comparação é feita sobre o custo por linha, não sobre o total.
     */
    private static final int AMOSTRA_UMA_A_UMA = 1_000;

    void main() {
        TransactionIngestor ingestor = new NioTransactionIngestor();
        List<Transaction> transacoes = ingestor.read(ARQUIVO);
        IO.println("Transações lidas do CSV: " + transacoes.size());

        //A conexao e aberta aqui e fechada aqui: o repositorio apenas a usa.
        try (Connection connection = abrirConexao()) {
            TransactionSQLRepository repository = new TransactionSQLRepository(connection);

            long nsPorLinhaUmaAUma = medirUmaAUma(repository, transacoes);
            long nsLoteTotal = medirEmLote(repository, transacoes);

            imprimirComparacao(transacoes.size(), nsPorLinhaUmaAUma, nsLoteTotal, repository.count());
            imprimirBuscas(repository);

        } catch (SQLException e) {
            throw new DataAccessException(
                    "não foi possível conectar ao MySQL em " + URL_PADRAO
                            + "; o banco está em pé? (docker compose up -d)", e);
        }
    }

    /**
     * Mede o caminho ingênuo: uma chamada a {@code save} por transação, cada uma em sua própria
     * transação de banco.
     *
     * <p>A tabela é limpa antes, para que as duas medições partam de uma tabela vazia. Sem isso
     * a segunda inserção trabalharia com o índice já povoado pela primeira e mediria outra
     * coisa.
     *
     * @return o custo médio por linha, em nanossegundos
     */
    private long medirUmaAUma(TransactionSQLRepository repository, List<Transaction> transacoes) {
        repository.deleteAll();
        List<Transaction> amostra = transacoes.subList(0, Math.min(AMOSTRA_UMA_A_UMA, transacoes.size()));

        long inicio = System.nanoTime();
        amostra.forEach(repository::save);
        long decorrido = System.nanoTime() - inicio;

        return decorrido / amostra.size();
    }

    /**
     * Mede a carga completa em lote, que é a inserção que a tarefa pede.
     *
     * @return o tempo total da carga, em nanossegundos
     */
    private long medirEmLote(TransactionSQLRepository repository, List<Transaction> transacoes) {
        repository.deleteAll();

        long inicio = System.nanoTime();
        repository.saveAll(transacoes);
        return System.nanoTime() - inicio;
    }

    private void imprimirComparacao(int linhas, long nsPorLinhaUmaAUma, long nsLoteTotal, long linhasNaTabela) {
        long nsPorLinhaLote = nsLoteTotal / linhas;

        IO.println("\n--- Inserção de " + formatar(linhas) + " transações ---");
        IO.println("Uma a uma (amostra de " + formatar(AMOSTRA_UMA_A_UMA) + " linhas): "
                + formatar(nsPorLinhaUmaAUma / 1_000) + " µs por linha");
        IO.println("Em lote (carga completa):  " + formatarMs(nsLoteTotal)
                + " no total, " + formatar(nsPorLinhaLote / 1_000) + " µs por linha");
        IO.println("O lote é " + (nsPorLinhaUmaAUma / Math.max(nsPorLinhaLote, 1))
                + "x mais rápido por linha");
        IO.println("Linhas na tabela após a carga: " + formatar(linhasNaTabela));
    }

    /**
     * Item 5: busca uma transação que existe e uma que não existe.
     *
     * <p>A variável é do tipo da interface de propósito. Este bloco é idêntico ao que a
     * {@code Main} executa sobre as implementações em memória — a troca de lista para tabela
     * MySQL não muda uma linha de quem consulta.
     */
    private void imprimirBuscas(TransactionRepository repository) {
        IO.println("\n--- Busca no MySQL ---");
        imprimirBusca(repository, CLIENTE_EXISTENTE);
        imprimirBusca(repository, CLIENTE_INEXISTENTE);
    }

    private void imprimirBusca(TransactionRepository repository, String originName) {
        repository.findByOriginName(originName).ifPresentOrElse(
                IO::println,
                () -> IO.println("Transação não encontrada para o cliente " + originName));
    }

    /**
     * Abre a conexão, aceitando sobreposição por variáveis de ambiente.
     *
     * <p>Não há {@code Class.forName("com.mysql.cj.jdbc.Driver")}. Desde o JDBC 4 o
     * {@link DriverManager} localiza o driver pelo {@code ServiceLoader}, bastando que o jar
     * esteja no classpath. Tutoriais antigos ainda ensinam o carregamento manual, que hoje é
     * apenas ruído.
     *
     * <p>As variáveis de ambiente existem para que a URL e as credenciais não fiquem presas ao
     * código. Os valores padrão são os do banco local de desenvolvimento e não têm uso fora
     * desta máquina.
     */
    private Connection abrirConexao() throws SQLException {
        String url = Objects.requireNonNullElse(System.getenv("ZENON_DB_URL"), URL_PADRAO);
        String usuario = Objects.requireNonNullElse(System.getenv("ZENON_DB_USER"), USUARIO_PADRAO);
        String senha = Objects.requireNonNullElse(System.getenv("ZENON_DB_PASSWORD"), SENHA_PADRAO);

        return DriverManager.getConnection(url, usuario, senha);
    }

    private String formatar(long valor) {
        return String.format(Locale.ROOT, "%,d", valor);
    }

    private String formatarMs(long nanos) {
        return String.format(Locale.ROOT, "%.0f ms", nanos / 1_000_000.0);
    }
}
