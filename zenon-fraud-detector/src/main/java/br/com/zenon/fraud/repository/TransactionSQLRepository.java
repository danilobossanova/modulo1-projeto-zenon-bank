package br.com.zenon.fraud.repository;

import br.com.zenon.fraud.Transaction;
import br.com.zenon.fraud.TransactionCustomer;
import br.com.zenon.fraud.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositório de transações sobre MySQL, via JDBC puro.
 *
 * <p>É a terceira implementação de {@link TransactionRepository}, ao lado das duas em memória.
 * A troca continua invisível para quem consulta: a interface fala de transações, não de listas,
 * mapas ou tabelas.
 *
 * <p>A conexão é recebida no construtor e <em>não</em> é fechada por esta classe — quem a abriu
 * é quem a fecha, normalmente em um {@code try}-com-recursos. A simetria com as outras
 * implementações é proposital: todas recebem no construtor a estrutura que vão consultar, sem
 * assumir o ciclo de vida dela.
 *
 * <p>A busca por nome de origem é servida pelo índice {@code idx_transactions_name_orig},
 * criado no {@code script.sql}. Sem ele o MySQL varreria a tabela inteira a cada consulta, o
 * que reproduziria dentro do banco exatamente o custo linear de
 * {@link TransactionListRepository}.
 */
public class TransactionSQLRepository implements TransactionRepository {

    /**
     * Quantas instruções acumular antes de enviar um lote ao servidor.
     *
     * <p>O lote existe para reduzir idas e voltas na rede, mas um lote ilimitado passa a ser o
     * problema: as instruções acumulam em memória no cliente e o pacote enviado pode estourar
     * o {@code max_allowed_packet} do servidor. Partir em blocos mantém os dois sob controle.
     */
    private static final int BATCH_SIZE = 1_000;

    private static final String INSERT = """
            INSERT INTO TRANSACTIONS
                (step, type, amount,
                 name_orig, old_balance_orig, new_balance_orig,
                 name_dest, old_balance_dest, new_balance_dest,
                 is_fraud, is_flagged_fraud)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * Busca pelo nome da origem.
     *
     * <p>O {@code ORDER BY id LIMIT 1} é o que faz esta implementação concordar com as outras
     * duas. Sem o {@code ORDER BY}, o MySQL pode devolver as linhas em qualquer ordem, e com
     * nomes de origem repetidos a resposta passaria a depender do plano de execução escolhido
     * naquele momento. Como {@code id} é {@code AUTO_INCREMENT}, ordenar por ele é ordenar por
     * ordem de inserção — a mesma "primeira ocorrência" que o {@code findFirst} devolve.
     */
    private static final String SELECT_BY_ORIGIN = """
            SELECT step, type, amount,
                   name_orig, old_balance_orig, new_balance_orig,
                   name_dest, old_balance_dest, new_balance_dest,
                   is_fraud, is_flagged_fraud
              FROM TRANSACTIONS
             WHERE name_orig = ?
             ORDER BY id
             LIMIT 1
            """;

    private final Connection connection;

    /**
     * Cria o repositório sobre uma conexão já estabelecida.
     *
     * @param connection conexão já aberta, cujo fechamento permanece com quem a abriu
     * @throws NullPointerException se a conexão for nula
     */
    public TransactionSQLRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection should not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>O {@code PreparedStatement} não é escolhido por desempenho, e sim porque ele separa a
     * instrução dos dados: o nome procurado viaja como parâmetro e nunca é concatenado ao SQL,
     * o que torna injeção de SQL impossível por construção. Montar a consulta com concatenação
     * de texto deixaria o conteúdo do argumento decidir a estrutura do comando.
     */
    @Override
    public Optional<Transaction> findByOriginName(String originName) {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_ORIGIN)) {
            statement.setString(1, originName);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapear(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("falha ao buscar a origem " + originName, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uma transação por ida ao servidor. Para inserir muitas linhas, prefira
     * {@link #saveAll(List)}: a diferença de tempo entre os dois caminhos é o que a tarefa
     * quer evidenciar.
     */
    @Override
    public void save(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction should not be null");

        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            vincular(statement, transaction);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("falha ao inserir a transação " + transaction, e);
        }
    }

    /**
     * Insere muitas transações em lotes, dentro de uma única transação de banco.
     *
     * <p>São três mudanças em relação a chamar {@link #save(Transaction)} em laço, e cada uma
     * economiza por um motivo diferente:
     *
     * <ul>
     *   <li><strong>{@code addBatch}/{@code executeBatch}</strong> acumula as instruções e as
     *       envia juntas, cortando uma ida e volta de rede por linha.</li>
     *   <li><strong>{@code setAutoCommit(false)}</strong> troca uma transação por linha por uma
     *       só para o conjunto. Com o autocommit ligado, cada inserção precisa ser durável
     *       antes da próxima começar, e o custo passa a ser de gravação em disco, não de rede.
     *       Em muitos casos essa linha economiza mais que o próprio lote.</li>
     *   <li><strong>{@code rewriteBatchedStatements=true} na URL</strong> é o que faz o driver
     *       do MySQL reescrever o lote como um único {@code INSERT} com várias tuplas. Sem esse
     *       parâmetro ele aceita o {@code addBatch} e ainda assim envia uma instrução por vez,
     *       de modo que o ganho do lote praticamente desaparece — sem erro nenhum, apenas
     *       lentidão.</li>
     * </ul>
     *
     * <p>O estado original do autocommit é restaurado no {@code finally}, porque a conexão
     * pertence a quem chamou e pode ser reaproveitada depois.
     *
     * @param transactions as transações a inserir, na ordem em que devem ser gravadas
     * @throws DataAccessException se a inserção falhar; a transação de banco é desfeita
     */
    public void saveAll(List<Transaction> transactions) {
        Objects.requireNonNull(transactions, "transactions should not be null");

        boolean autoCommitOriginal = true;
        try {
            autoCommitOriginal = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                int acumuladas = 0;

                for (Transaction transaction : transactions) {
                    vincular(statement, transaction);
                    statement.addBatch();

                    if (++acumuladas % BATCH_SIZE == 0) {
                        statement.executeBatch();
                    }
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            desfazer(e);
            throw new DataAccessException("falha ao inserir " + transactions.size() + " transações", e);
        } finally {
            restaurarAutoCommit(autoCommitOriginal);
        }
    }

    /**
     * Apaga todas as linhas da tabela.
     *
     * <p>Serve à medição de desempenho: comparar inserção linha a linha com inserção em lote
     * exige que as duas partam de uma tabela vazia, senão a segunda carrega o índice já
     * povoado pela primeira e mede outra coisa.
     *
     * <p>{@code TRUNCATE} em vez de {@code DELETE} porque ele recria a tabela em vez de
     * registrar a remoção linha por linha, e reinicia o {@code AUTO_INCREMENT} — o que mantém
     * as medições realmente equivalentes.
     *
     * @throws DataAccessException se a limpeza falhar
     */
    public void deleteAll() {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("TRUNCATE TABLE TRANSACTIONS");
        } catch (SQLException e) {
            throw new DataAccessException("falha ao limpar a tabela TRANSACTIONS", e);
        }
    }

    /**
     * Conta as linhas da tabela, para conferir o resultado de uma carga.
     *
     * @return a quantidade de transações gravadas
     * @throws DataAccessException se a contagem falhar
     */
    public long count() {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM TRANSACTIONS")) {

            result.next();
            return result.getLong(1);
        } catch (SQLException e) {
            throw new DataAccessException("falha ao contar as transações", e);
        }
    }

    /**
     * Preenche os parâmetros do {@code INSERT} a partir da transação.
     *
     * <p>Valores monetários usam {@code setBigDecimal} contra colunas {@code DECIMAL}, e não
     * {@code setDouble} contra {@code DOUBLE}: é a mesma decisão que o código já tomou em
     * memória, estendida ao armazenamento. Guardar dinheiro em ponto flutuante binário
     * desfaria a exatidão do {@code BigDecimal} no momento da gravação.
     */
    private void vincular(PreparedStatement statement, Transaction transaction) throws SQLException {
        TransactionCustomer origin = transaction.origin();
        TransactionCustomer recipient = transaction.recipient();

        statement.setInt(1, transaction.step());
        statement.setString(2, transaction.type().name());
        statement.setBigDecimal(3, transaction.amount());

        statement.setString(4, origin.name());
        statement.setBigDecimal(5, origin.oldBalance());
        statement.setBigDecimal(6, origin.newBalance());

        statement.setString(7, recipient.name());
        statement.setBigDecimal(8, recipient.oldBalance());
        statement.setBigDecimal(9, recipient.newBalance());

        statement.setBoolean(10, transaction.isFraud());
        statement.setBoolean(11, transaction.isFlaggedFraud());
    }

    /**
     * Reconstrói a transação a partir da linha corrente do resultado.
     *
     * <p>O {@code type} volta por {@link TransactionType#valueOf(String)}, o que torna a coluna
     * autovalidada: um valor gravado fora do enum falha aqui, em vez de circular pelo código
     * como texto qualquer.
     */
    private Transaction mapear(ResultSet result) throws SQLException {
        TransactionCustomer origin = new TransactionCustomer(
                result.getString("name_orig"),
                result.getBigDecimal("old_balance_orig"),
                result.getBigDecimal("new_balance_orig"));

        TransactionCustomer recipient = new TransactionCustomer(
                result.getString("name_dest"),
                result.getBigDecimal("old_balance_dest"),
                result.getBigDecimal("new_balance_dest"));

        return new Transaction(
                result.getInt("step"),
                TransactionType.valueOf(result.getString("type")),
                result.getBigDecimal("amount"),
                origin,
                recipient,
                result.getBoolean("is_fraud"),
                result.getBoolean("is_flagged_fraud"));
    }

    /**
     * Desfaz a transação de banco em curso.
     *
     * <p>Uma falha no próprio rollback é anexada como exceção suprimida da original. Deixá-la
     * substituir a original esconderia o motivo real da falha atrás de um sintoma.
     */
    private void desfazer(SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException falhaNoRollback) {
            original.addSuppressed(falhaNoRollback);
        }
    }

    private void restaurarAutoCommit(boolean valorOriginal) {
        try {
            connection.setAutoCommit(valorOriginal);
        } catch (SQLException e) {
            throw new DataAccessException("falha ao restaurar o autocommit da conexão", e);
        }
    }
}
