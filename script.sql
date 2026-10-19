-- Esquema da tabela TRANSACTIONS do Zenón Bank.
--
-- O docker-compose monta este arquivo em /docker-entrypoint-initdb.d, e o MySQL o executa
-- sozinho na primeira inicialização do volume. Rodar à mão só é necessário se o volume já
-- existir; veja o README.

CREATE TABLE IF NOT EXISTS TRANSACTIONS
(
    -- O record Transaction não tem chave natural: o PaySim não traz identificador de
    -- transação, e nome de origem repete. Daí a chave sintética.
    id               BIGINT        NOT NULL AUTO_INCREMENT,

    step             INT           NOT NULL,
    type             VARCHAR(10)   NOT NULL,

    -- DECIMAL, nunca DOUBLE ou FLOAT. É a mesma razão que levou o BigDecimal ao código:
    -- ponto flutuante binário não representa valores decimais exatamente, e o erro acumula.
    -- A soma das 6,3 milhões de transações do arquivo completo erra cinco centavos em double.
    amount           DECIMAL(18, 2) NOT NULL,

    name_orig        VARCHAR(20)   NOT NULL,
    old_balance_orig DECIMAL(18, 2) NOT NULL,
    new_balance_orig DECIMAL(18, 2) NOT NULL,

    name_dest        VARCHAR(20)   NOT NULL,
    old_balance_dest DECIMAL(18, 2) NOT NULL,
    new_balance_dest DECIMAL(18, 2) NOT NULL,

    is_fraud         BOOLEAN       NOT NULL,
    is_flagged_fraud BOOLEAN       NOT NULL,

    PRIMARY KEY (id),

    -- Este índice é a tarefa 6 em SQL. Sem ele, findByOriginName vira varredura completa da
    -- tabela, O(n), exatamente como o TransactionListRepository; com ele, o MySQL percorre
    -- uma árvore B+ e o custo passa a ser logarítmico. A estrutura de dados continua
    -- decidindo o desempenho da busca, só que agora dentro do banco.
    KEY idx_transactions_name_orig (name_orig)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
