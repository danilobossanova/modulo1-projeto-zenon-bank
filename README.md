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

## ✅ Tarefa 6 — Complexidade algorítmica: List vs Map

A mesma pergunta respondida por duas estruturas de dados sobre 100.000 transações, com os tempos medidos: busca linear contra busca indexada.

### O que o enunciado pediu

| # | Requisito | Status |
|---|---|---|
| 1 | Branch `tarefa/06-benchmark`, com commit e push da entrega | 🚧 ¹ |
| 2 | `TransactionIngestor` carregando 100.000 transações | ✅ ² |
| 3 | `TransactionListRepository` buscando por cliente de origem e devolvendo `Optional<Transaction>`, testado na `Main` com valor existente e inexistente | ✅ ³ |
| 4 | Busca pelo `nameOrig` da última transação (`C1868032458`), o pior caso, medida com `System.nanoTime()` | ✅ |
| 5 | `TransactionRepository` abstraído em interface, com a classe de lista como implementação | ✅ |
| 6 | Segunda implementação carregando em `Map<String, Transaction>`, com nova medição | ✅ |

¹ Branch criada e commits da entrega feitos. O push é o passo que fecha a tarefa.

² `LAST_LINE_TO_READ = 100_001`: o cabeçalho mais as 100.000 linhas de dados, mantendo a interpretação das tarefas anteriores. O próprio enunciado confirma essa leitura — só com o cabeçalho fora da contagem é que `C1868032458` cai na última posição da lista, como o item 4 pressupõe. Contando o cabeçalho, a última seria `C707662966`.

³ A linha impressa para `C1231006815` é o `toString()` gerado pelo record, sem formatação própria: idêntica ao exemplo do enunciado.

### Estrutura

| Arquivo | Papel |
|---|---|
| `TransactionRepository` | a interface: o contrato de busca, sem compromisso de custo |
| `TransactionListRepository` | varredura linear, com `stream().filter().findFirst()` |
| `TransactionMapRepository` | índice `Map<String, Transaction>` montado no construtor |

A `Main` declara a variável como `TransactionRepository`, então o código de busca não sabe qual estrutura responde. É a mesma lição que a interface `TransactionIngestor` trouxe na tarefa 3, agora aplicada ao acesso aos dados: trocar a implementação não altera uma linha de quem consulta.

### Resultado

```
--- Tarefa 6: busca por cliente de origem ---
Transação não encontrada para o cliente C12345
Transaction[step=1, type=PAYMENT, amount=9839.64, origin=TransactionCustomer[name=C1231006815, oldBalance=170136.0, newBalance=160296.36], recipient=TransactionCustomer[name=M1979787155, oldBalance=0.0, newBalance=0.0], isFraud=false, isFlaggedFraud=false]

--- Tarefa 6: benchmark de busca (pior caso, C1868032458) ---
List, varredura O(n): 3,883,380 ns (3.883 ms)
Map, indice O(1):     163 ns (0.000 ms)
Consulta 23824x mais rapida no Map

Custo de montar o indice, O(n) pago uma vez: 20,941,000 ns (20.941 ms)
Ponto de equilibrio: o indice se paga a partir de 5 buscas
```

A `Main` também imprime as cinco análises da tarefa 5 antes deste bloco. Os números daquela seção agora refletem 100.000 transações — 116 fraudes, prejuízo 62.823.097,20 — e não as 50.000 do enunciado daquela tarefa.

### Por que o pior caso é a última transação

`findFirst()` curto-circuita: para no primeiro match e abandona o resto do stream. Buscar `C1231006815`, a primeira transação, avalia o predicado **uma** vez; buscar `C1868032458`, a última, avalia **100.000** vezes. É o mesmo código com cem mil vezes mais trabalho, e é essa assimetria que o item 4 mede.

Vale notar o que *não* foi escrito: uma variante com `filter().toList().get(0)` percorreria a lista inteira em toda busca, inclusive para o cliente da primeira posição, e apagaria justamente o contraste que a tarefa quer evidenciar.

### Decisões de implementação

**A função de merge no `toMap` não é precaução.** O `toMap` de dois argumentos lança `IllegalStateException` na primeira chave repetida. Nas 100.000 primeiras transações do PaySim todas as origens são distintas, então ele passaria — por sorte da amostra. No primeiro milhão há 241 origens repetidas, e no arquivo completo 9.313. A sobrecarga de três argumentos recebe `(primeira, proxima) -> primeira`.

Manter a **primeira** ocorrência, e não a última, é o que faz o `Map` concordar com o `findFirst` da lista. Sem essa concordância as duas implementações responderiam transações diferentes para a mesma pergunta, e comparar os tempos não mediria nada.

**A medição cerca um lote, não uma chamada.** O `System.nanoTime()` avança em passos da ordem de 100 ns no Windows, e a consulta ao `Map` custa menos do que isso. Cronometrada sozinha, ela reportava 200 ns — dois ticks do cronômetro, não o custo da busca. Medindo um lote e dividindo pelo número de buscas, o valor caiu para cerca de 160 ns e passou a se repetir entre execuções. Um laço de aquecimento precede o lote, porque as primeiras execuções rodam interpretadas, antes de o JIT compilar o caminho.

**O resultado é consumido.** Se a busca fosse cronometrada e descartada, o compilador poderia concluir que a chamada não tem efeito observável e removê-la — o benchmark mediria um laço vazio. A validação de que o cliente foi encontrado cumpre esse papel.

**O custo do índice entra no relatório.** O `Map` é mais rápido por consulta, mas cobra O(n) adiantado: montar o índice leva cerca de 22 ms, seis vezes o custo de uma única varredura. Para **uma** busca a lista ganha. O programa imprime o ponto de equilíbrio porque uma comparação que omite o custo de construção favorece o `Map` indevidamente — prova menos do que parece provar.

### Execução

```bash
cd zenon-fraud-detector
mvn -q clean compile
java -cp target/classes br.com.zenon.Main
```

Os CSVs ficam em `data/` e não são versionados.

O projeto exige **JDK 25**: a `Main` usa o método de instância `void main()` e `IO.println`, finalizados nessa versão. Se algum acento aparecer quebrado, o console está lendo como UTF-8 o que a JVM escreveu no code page do Windows; nesse caso rode com `-Dstdout.encoding=UTF-8`.
