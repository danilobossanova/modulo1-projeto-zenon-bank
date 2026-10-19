package br.com.zenon.fraud.repository;

import br.com.zenon.fraud.Transaction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Busca de transações por varredura linear sobre a lista carregada.
 *
 * <p>A classe não lê arquivos nem conhece o formato do CSV: recebe as transações prontas
 * e apenas as consulta.
 *
 * <p>O custo linear é deliberado. Esta implementação é a referência contra a qual se mede
 * o ganho de um índice de hash: cada busca percorre a lista até encontrar o elemento, de
 * modo que o tempo de resposta cresce com o tamanho da coleção e, principalmente, com a
 * posição do alvo dentro dela.
 *
 * <p>A lista recebida <em>não</em> é copiada, para não duplicar centenas de milhares de
 * referências em memória. Em troca, quem constrói o repositório não deve modificá-la depois.
 */
public class TransactionListRepository {

    private final List<Transaction> transactions;

    /**
     * Cria o repositório sobre uma lista de transações já carregada.
     *
     * @param transactions as transações a consultar, na ordem em que foram lidas do arquivo
     * @throws NullPointerException se a lista for nula
     */
    public TransactionListRepository(List<Transaction> transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions should not be null");
    }

    /**
     * Busca a primeira transação cujo cliente de origem tenha o nome informado.
     *
     * <p>A busca é uma varredura linear: O(n) no tamanho da lista. O custo depende da
     * posição do elemento, porque {@code findFirst} abandona o stream no primeiro match —
     * um cliente no início responde de imediato, um no fim percorre tudo.
     *
     * <p>A comparação mantém o nome lido do arquivo à esquerda do {@code equals}, para que
     * um {@code originName} nulo resulte em busca sem resultado em vez de
     * {@link NullPointerException}.
     *
     * @param originName nome do cliente de origem (ex.: {@code C1231006815})
     * @return a transação, ou vazio se nenhuma tiver essa origem
     */
    public Optional<Transaction> findByOriginName(String originName) {
        return transactions.stream()
                .filter(transaction -> transaction.origin().name().equals(originName))
                .findFirst();
    }
}
