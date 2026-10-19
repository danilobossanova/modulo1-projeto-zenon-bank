# 🏦 Zenón Bank - Projeto Prático: Fundamentos Java

Bem-vindo ao repositório base do projeto prático da disciplina de Fundamentos Java. Este projeto simula o backend de uma fintech fictícia, o **Zenón Bank**, onde você aplicará conceitos desde manipulação de tipos básicos até processamento de alta performance e concorrência.

## 📋 Sobre o Projeto

O objetivo é consolidar os conhecimentos adquiridos em aula através de um cenário realista. Você lidará com:
* Modelagem de dados com **Records**.
* API de Collections e Streams.
* Manipulação de I/O de arquivos grandes.
* Tratamento de Exceções.
* Integração com Banco de Dados.
* Concorrência e Paralelismo.

Siga o passo a passo do quadro de atividades (Trello) do projeto prático dessa disciplina, disponível nos vídeos de projeto prático da disciplina.

## 📦 Requisitos

* **Git e GitHub**: para controle de versões e portifólio de repositórios.
* **Java JDK**: 25 (LTS).
* **IDE**: IntelliJ IDEA ou Eclipse.
* **Docker e Docker Compose**: Para a tarefa de Banco de Dados.

---

## ✅ Tarefa 9 — JDBC e MySQL: a importância da inserção em lote

As transações saem do CSV e vão para um MySQL real, em Docker, com o tempo de inserção medido em três caminhos diferentes.

### O que o enunciado pediu

| # | Requisito | Status |
|---|---|---|
| 1 | Branch `tarefa/09-jdbc`, com commit e push da entrega | 🚧 ¹ |
| 2 | `docker-compose.yml` subindo o MySQL com Docker | ✅ |
| 3 | `script.sql` criando a tabela `TRANSACTIONS`, executado no MySQL | ✅ ² |
| 4 | Driver do MySQL no `pom.xml`; `TransactionSQLRepository` implementando `TransactionRepository` com `findByOriginName`; `save(Transaction)` na interface e em todas as implementações | ✅ |
| 5 | `DBMain` lendo 10 mil transações do CSV, inserindo no banco, medindo o tempo e buscando `C1231006815` e `C12345` | ✅ |

¹ Branch criada e commits da entrega feitos. O push é o passo que fecha a tarefa.

² O `docker-compose.yml` monta o `script.sql` em `/docker-entrypoint-initdb.d`, então o MySQL o executa sozinho na primeira subida do volume. Conferido com `DESCRIBE TRANSACTIONS` e `SHOW INDEX`.

### A medição

O número que a tarefa pede é o da última linha. As outras duas estão aqui porque "742 ms" não significa nada sozinho: rápido ou lento só existe em comparação com algo.

| Caminho | 10.000 linhas | Por linha | Ganho |
|---|---|---|---|
| Uma a uma, com autocommit | ~61 s ³ | ~6.071 µs | — |
| Em lote, **sem** `rewriteBatchedStatements` | 14.687 ms | 1.468 µs | 4x |
| Em lote, **com** `rewriteBatchedStatements` | **522–742 ms** | 52–74 µs | **~100x** |

³ Extrapolado de uma amostra de 1.000 linhas. O custo por linha desse caminho é constante, então rodar as 10.000 consumiria um minuto por execução sem acrescentar informação.

A linha do meio é o achado da tarefa. O código das duas últimas é **idêntico** — mesmo `addBatch`, mesmo `executeBatch`, mesma transação de banco. Só a URL de conexão muda. Sem o parâmetro, o driver do MySQL aceita o lote e ainda assim envia uma instrução por vez: sobra um ganho de 4x em vez de 100x, e nenhum erro avisa que isso está acontecendo.

Ou seja: **usar `addBatch` não garante que você está inserindo em lote.** Um código que parece correto entrega 25 vezes menos do que poderia, por causa de um parâmetro de conexão.

### A saída

```
Transações lidas do CSV: 10000

--- Inserção de 10,000 transações ---
Uma a uma (amostra de 1,000 linhas): 6,071 µs por linha
Em lote (carga completa):  742 ms no total, 74 µs por linha
O lote é 81x mais rápido por linha
Linhas na tabela após a carga: 10,000

--- Busca no MySQL ---
Transaction[step=1, type=PAYMENT, amount=9839.64, origin=TransactionCustomer[name=C1231006815, oldBalance=170136.00, newBalance=160296.36], recipient=TransactionCustomer[name=M1979787155, oldBalance=0.00, newBalance=0.00], isFraud=false, isFlaggedFraud=false]
Transação não encontrada para o cliente C12345
```

### Estrutura

| Arquivo | Papel |
|---|---|
| `docker-compose.yml` | MySQL 8.4, com healthcheck e o `script.sql` como init |
| `script.sql` | tabela `TRANSACTIONS` e o índice em `name_orig` |
| `TransactionSQLRepository` | a terceira implementação de `TransactionRepository` |
| `DataAccessException` | converte `SQLException` em não checada |
| `DBMain` | lê o CSV, carrega o banco, mede e consulta |

### Decisões de implementação

**`setAutoCommit(false)` economiza por um motivo diferente do lote.** O lote corta idas e voltas de rede; o autocommit desligado troca 10.000 transações de banco por uma. Com autocommit, cada inserção precisa estar durável em disco antes de a próxima começar, e o gargalo passa a ser `fsync`, não rede. É o que explica os 6 ms por linha do primeiro caminho: não é o MySQL sendo lento, é o disco sendo honesto.

**O índice em `name_orig` é a tarefa 6 em SQL.** Sem ele, `findByOriginName` vira varredura completa da tabela — O(n), exatamente o que o `TransactionListRepository` faz em memória. Com ele, o MySQL percorre uma árvore B+. A estrutura de dados continua decidindo o custo da busca; só mudou de lado.

**`DECIMAL`, nunca `DOUBLE`.** É a decisão do `BigDecimal` estendida ao armazenamento: guardar dinheiro em ponto flutuante binário desfaria na gravação a exatidão que o código mantém em memória.

Isso tem uma consequência que vale registrar. Compare a saída da busca com a linha do CSV: o arquivo traz `170136.0`, o banco devolve `170136.00`. A coluna é `DECIMAL(18,2)` e o MySQL normaliza a escala para 2. Como `BigDecimal.equals` compara **também a escala**, a `Transaction` que volta do banco **não é `equals`** à que saiu do CSV, embora represente o mesmo dado. É o mesmo `equals` contra `compareTo` da tarefa 5, reaparecendo na camada de persistência.

**`DataAccessException` mantém a interface livre de JDBC.** `SQLException` é checada. Declará-la em `TransactionRepository.save` espalharia JDBC por um contrato que também é implementado por uma `List` e um `Map`, e obrigaria quem usa as versões em memória a tratar uma exceção que ali nunca pode ocorrer. A conversão para não checada é o que permite ao contrato falar de transações, e não da tecnologia que as guarda. A causa original nunca é descartada.

**`ORDER BY id LIMIT 1` na busca.** Sem o `ORDER BY`, o MySQL pode devolver as linhas em qualquer ordem, e com nomes de origem repetidos a resposta passaria a depender do plano de execução daquele momento. Como `id` é `AUTO_INCREMENT`, ordenar por ele é ordenar por ordem de inserção — a mesma "primeira ocorrência" que o `findFirst` devolve na lista e que o `putIfAbsent` preserva no mapa. As três implementações respondem a mesma transação para a mesma pergunta.

**`PreparedStatement` não é sobre desempenho.** Ele separa a instrução dos dados: o nome procurado viaja como parâmetro e nunca é concatenado ao SQL, o que torna injeção impossível por construção. Montar a consulta com concatenação deixaria o conteúdo do argumento decidir a estrutura do comando.

**`save` nas implementações em memória.** A de lista acrescenta ao fim da lista recebida no construtor — e como essa lista não é copiada, a gravação é visível para quem a passou. Não é descuido: é a consequência de o repositório ser uma vista sobre a coleção de outro, e está documentada no método. A de mapa usa `putIfAbsent`, e não `put`, para não divergir das outras duas quando um nome de origem repetir.

**A conexão é recebida, não criada.** Quem abre é quem fecha, em `try`-com-recursos na `DBMain`. Mantém a simetria com as outras implementações, que também recebem no construtor a estrutura que vão consultar, sem assumir o ciclo de vida dela.

**Sem `Class.forName`.** Desde o JDBC 4 o `DriverManager` localiza o driver pelo `ServiceLoader`, bastando o jar no classpath. O carregamento manual que os tutoriais antigos ensinam é ruído hoje.

**O driver entra com escopo `runtime`.** Nenhuma classe do projeto importa o driver, e escopo `compile` permitiria que um import acidental amarrasse o código ao MySQL. O artefato também mudou de nome: `mysql:mysql-connector-java` foi descontinuado em favor de `com.mysql:mysql-connector-j`.

**O limite do ingestor foi para 10.000**, como o item 5 pede. Isso muda a saída da `Main`: as análises das tarefas 5 e 6 passam a refletir 10.000 transações em vez de 100.000.

### Como foi verificado

| Cenário | Resultado |
|---|---|
| `docker compose up -d` em máquina sem a imagem em cache | container saudável; tabela e índice criados pelo `script.sql` |
| Carga de 10.000 em lote | 10.000 linhas na tabela, conferidas com `COUNT(*)` |
| Busca por `C1231006815` | transação encontrada, com os valores do CSV |
| Busca por `C12345` | `Optional` vazio e mensagem de não encontrado |
| Lote sem `rewriteBatchedStatements` | 14.687 ms, contra 522–742 ms com o parâmetro |

### Execução

#### Subir o banco

Da raiz do repositório:

```bash
docker compose up -d
```

O healthcheck existe porque o container responde na porta antes de o servidor aceitar conexões. Sem esperar por ele, rodar a aplicação logo após subir o banco falha de forma intermitente com `Communications link failure`.

Para recriar a tabela do zero é preciso apagar o volume, porque o MySQL só executa o `script.sql` quando o diretório de dados está vazio:

```bash
docker compose down -v && docker compose up -d
```

#### Rodar a aplicação

```bash
cd zenon-fraud-detector
mvn -q clean package
java -cp "target/classes;target/dependency/*" br.com.zenon.DBMain
```

O `package`, e não o `compile`, porque é ele que copia o driver para `target/dependency`. Pela IDE isso não é necessário: o IntelliJ monta o classpath a partir do `pom.xml`, e basta rodar `br.com.zenon.DBMain` com o working directory em `zenon-fraud-detector`.

URL e credenciais podem ser sobrepostas por `ZENON_DB_URL`, `ZENON_DB_USER` e `ZENON_DB_PASSWORD`. Os valores padrão são os do banco local de desenvolvimento e não têm uso fora desta máquina.

Os CSVs ficam em `data/` e não são versionados.

O projeto exige **JDK 25**: as classes de entrada usam o método de instância `void main()` e `IO.println`, finalizados nessa versão.
