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

## ✅ Tarefa 5 — Stream API

Processamento declarativo de coleções: cinco análises de fraude sobre as 50.000 primeiras transações do CSV, construídas com a Stream API.

### O que o enunciado pediu

| # | Requisito | Status |
|---|---|---|
| 1 | Branch `tarefa/05-streams`, com commit e push da entrega | ✅ |
| 2 | Carregar 50.000 linhas em `TransactionIngestor` e criar a classe `FraudAnalyzer` usando a Stream API | ✅ ¹ |
| 2.1 | Apenas transações com `isFraud == true`, imprimindo o tamanho da lista | ✅ |
| 2.2 | As 3 fraudes de maior valor (`amount`) | ✅ |
| 2.3 | Os nomes de origem (`nameOrig`) dessas fraudes, sem repetições, nos 5 maiores suspeitos | ✅ |
| 2.4 | Prejuízo total causado pelas fraudes (soma dos `amount`) | ✅ |
| 2.5 | Quantas fraudes ocorreram por tipo de transação | ✅ |
| 3 | `Main` invoca cada um dos métodos e imprime os resultados | ✅ ² |

¹ `LAST_LINE_TO_READ = 50_001`: o cabeçalho mais 50.000 linhas de dados. Mantém a interpretação adotada na tarefa 3, de que o limite se refere a transações e não a linhas do arquivo.

² Saída conferida contra o exemplo do enunciado: as 15 linhas são idênticas, na mesma ordem.

### O que foi implementado

Cada item virou um método de `FraudAnalyzer`. A classe recebe a lista já carregada e não conhece o formato do arquivo — não lê nada, apenas consulta:

| Item | Método | Operações da Stream API |
|---|---|---|
| 2.1 | `countFrauds()` | `filter` + `count` |
| 2.2 | `findHighestValueFrauds(limit)` | `filter` + `sorted` + `limit` |
| 2.3 | `findTopSuspectOrigins(limit)` | `filter` + `sorted` + `map` + `distinct` + `limit` |
| 2.4 | `totalFraudLoss()` | `filter` + `map` + `reduce` |
| 2.5 | `countFraudsByType()` | `filter` + `collect(groupingBy + counting)` |

Como lembra a nota do enunciado, um stream não pode ser reutilizado: cada método abre o seu próprio a partir da mesma `List<Transaction>`. O arquivo é lido uma única vez, na `Main`.

### Saída

```
1. Total de Fraudes: 100
2. Top 3 Fraudes de Maior Valor:
10000000.00
10000000.00
5460002.91
3. Clientes Suspeitos:
C7162498
C351297720
C666654362
C1588880909
C2047521920
4. Prejuízo Total: 57393771.84
5. Fraudes por Tipo:
 - CASH_OUT: 51
 - TRANSFER: 49
```

### Decisões de implementação

**Ordenação por `compareTo`, não por `equals`.** O CSV traz alguns valores em notação científica (`1.0E7`). Para o `equals` do `BigDecimal`, `1.0E7` e `10000000.00` são valores diferentes, porque ele compara também o *scale*. O `compareTo` compara apenas o valor numérico, e é o que coloca as duas fraudes de dez milhões no topo da linha 2 da saída.

**`setScale(2)` antes do `toPlainString()` na impressão.** São dois problemas distintos do mesmo dado: `toPlainString` desfaz o formato exponencial, mas o *scale* negativo de `1.0E7` ainda imprimiria `10000000`, sem casas decimais. O `setScale(2)` fecha a diferença com o enunciado.

**Ordenação antes do `distinct()` no item 2.3.** Os "5 maiores clientes suspeitos" são os das maiores fraudes, não os 5 primeiros a aparecer no arquivo. Ordenar por `amount` antes de reduzir aos nomes é o que garante isso.

**`EnumMap` no `groupingBy` do item 2.5.** O `groupingBy` devolve um `HashMap`, cuja ordem de iteração não é garantida. A sobrecarga de três argumentos aceita uma fábrica de mapa: com `EnumMap`, a saída segue a ordem de declaração de `TransactionType` e `CASH_OUT` sai antes de `TRANSFER`, como no enunciado.

Só aparecem os tipos com ao menos uma fraude. O `groupingBy` não cria chave para grupo vazio, então `CASH_IN`, `DEBIT` e `PAYMENT` ficam fora do mapa em vez de sair com zero.

### Organização da `Main`

Cada item do enunciado virou um método privado de impressão, de modo que `main` se leia como o roteiro da tarefa. Os limites pedidos são constantes nomeadas (`TOP_FRAUDES`, `TOP_SUSPEITOS`) e a formatação monetária tem um ponto único. A `Main` orquestra e formata; as análises ficam em `FraudAnalyzer` e a leitura em `TransactionIngestor`.

### Execução

```bash
cd zenon-fraud-detector
mvn -q clean compile
java -cp target/classes br.com.zenon.Main
```

Os CSVs ficam em `data/` e não são versionados.

O projeto exige **JDK 25**: a `Main` usa o método de instância `void main()` e `IO.println`, finalizados nessa versão. Se o item 4 aparecer como `Prejuzo`, o console está lendo como UTF-8 o que a JVM escreveu no code page do Windows; nesse caso rode com `-Dstdout.encoding=UTF-8`.
