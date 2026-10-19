package br.com.zenon.fraud.repository;

import br.com.zenon.fraud.Transaction;

import java.util.Optional;

/**
 * Contrato de consulta de transações já carregadas em memória.
 *
 * <p>Existe uma implementação para cada estrutura de dados que se quer comparar:
 *
 * <ul>
 *   <li>{@link TransactionListRepository} — varredura linear sobre {@code List}: O(n)</li>
 *   <li>{@link TransactionMapRepository} — índice de hash sobre {@code Map}: O(1) por consulta</li>
 * </ul>
 *
 * <p>As duas devolvem exatamente o mesmo resultado para a mesma pergunta. O que muda é
 * quanto custa chegar nele — que é justamente o ponto da comparação.
 *
 * <p>O custo da consulta <em>não</em> faz parte deste contrato, e é por isso que a
 * implementação pode ser trocada sem que o código cliente mude: quem chama depende da
 * pergunta, não da estrutura de dados que a responde.
 */
public interface TransactionRepository {

    /**
     * Busca uma transação pelo nome do cliente de origem.
     *
     * <p>Quando mais de uma transação tem a mesma origem, toda implementação deve devolver
     * a <em>primeira</em> na ordem de leitura. Sem essa garantia o resultado dependeria da
     * estrutura de dados escolhida, e trocar de implementação mudaria a resposta.
     *
     * @param originName nome do cliente de origem (ex.: {@code C1231006815})
     * @return a transação, ou vazio se nenhuma tiver essa origem
     */
    Optional<Transaction> findByOriginName(String originName);

    /**
     * Grava uma transação no repositório.
     *
     * <p>Note que o método não declara {@code throws SQLException}, embora a implementação em
     * banco só possa falhar por motivos de SQL. Declará-la aqui espalharia JDBC por uma
     * interface que também é implementada por coleções em memória, e obrigaria quem usa a
     * lista a tratar uma exceção que nunca pode ocorrer. A implementação de banco converte a
     * exceção checada em {@link DataAccessException}; é o que mantém este contrato livre da
     * tecnologia que o atende.
     *
     * <p>Cada implementação documenta o que "gravar" significa para a sua estrutura. Em
     * particular, as implementações em memória gravam na coleção que receberam no construtor,
     * e portanto são visíveis para quem passou essa coleção.
     *
     * @param transaction a transação a gravar
     * @throws NullPointerException se a transação for nula
     * @throws DataAccessException  se a gravação falhar
     */
    void save(Transaction transaction);
}
