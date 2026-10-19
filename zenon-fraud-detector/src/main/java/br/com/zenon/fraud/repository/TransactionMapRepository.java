package br.com.zenon.fraud.repository;

import br.com.zenon.fraud.Transaction;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Busca de transações por índice de hash, montado uma única vez na construção.
 *
 * <p>A consulta é O(1): o {@code Map} calcula o hash do nome e salta direto para o balde
 * correspondente, sem percorrer as transações. Em troca, montar o índice é O(n) e esse
 * custo é pago na construção.
 *
 * <p>Esse é o trade-off que a comparação quer evidenciar. Para uma <em>única</em> busca a
 * varredura linear de {@link TransactionListRepository} sai na frente, porque não paga
 * índice nenhum; o {@code Map} só compensa depois de um certo número de consultas.
 *
 * <p>Diferente de {@link TransactionListRepository}, aqui as transações <em>são</em>
 * percorridas na construção e suas referências ficam retidas no mapa: uma entrada por
 * cliente de origem distinto.
 */
public class TransactionMapRepository implements TransactionRepository {

    private final Map<String, Transaction> index;

    /**
     * Indexa as transações pelo nome do cliente de origem.
     *
     * <p>A função de merge {@code (primeira, proxima) -> primeira} não é apenas defensiva.
     * Sem ela, o {@code toMap} de dois argumentos lança {@link IllegalStateException} na
     * primeira origem repetida que encontrar. Isso não acontece nas 100.000 primeiras
     * transações do PaySim, onde todas as origens são distintas, mas acontece 241 vezes no
     * primeiro milhão — o código funcionaria por sorte da amostra.
     *
     * <p>Manter a primeira ocorrência é também o que faz esta implementação concordar com o
     * {@code findFirst} de {@link TransactionListRepository}. Sem essa concordância as duas
     * responderiam transações diferentes para a mesma pergunta, e comparar os tempos não
     * teria sentido.
     *
     * @param transactions as transações a indexar, na ordem em que foram lidas do arquivo
     * @throws NullPointerException se a lista for nula
     */
    public TransactionMapRepository(List<Transaction> transactions) {
        Objects.requireNonNull(transactions, "transactions should not be null");
        this.index = transactions.stream()
                .collect(Collectors.toMap(
                        transaction -> transaction.origin().name(),
                        Function.identity(),
                        (primeira, proxima) -> primeira));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uma consulta ao índice, em tempo constante.
     *
     * <p>{@code Optional.ofNullable} é obrigatório aqui: {@link Map#get(Object)} devolve
     * {@code null} para chave ausente, e {@code Optional.of(null)} lançaria
     * {@link NullPointerException} em vez de representar a ausência.
     */
    @Override
    public Optional<Transaction> findByOriginName(String originName) {
        return Optional.ofNullable(index.get(originName));
    }
}
