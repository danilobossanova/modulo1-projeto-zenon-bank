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

## ✅ Tarefa 7 — I/O eficiente: carregar na RAM vs processar em stream

Processamento do arquivo completo do PaySim — 6.362.620 transações, cerca de 493 MB — com o heap limitado, sem nunca carregar o arquivo na memória.

### O que o enunciado pediu

| # | Requisito | Status |
|---|---|---|
| 1 | Branch `tarefa/07-nio`, com commit e push da entrega | 🚧 ¹ |
| 2 | Remover o limite do `TransactionIngestor`, ver o `OutOfMemoryError` com `-Xmx128m` e devolver o limite a 100 mil | ✅ ² |
| 3 | `TransactionReport` usando `java.nio.file.Files.lines(Path)` | ✅ |
| 4 | Processar o arquivo inteiro calculando apenas total de linhas, total de fraudes e valor transacionado | ✅ |
| 5 | `ReportMain` sobre o PaySim completo, com `-Xmx128m` | ✅ ³ |

¹ Branch criada e commit da entrega feito. O push é o passo que fecha a tarefa.

² O erro foi reproduzido e está registrado abaixo. O limite voltou a `100_001` antes do commit, como o enunciado pede.

³ Os três números saem idênticos ao exemplo do enunciado.

### O `OutOfMemoryError` do item 2

Com `LAST_LINE_TO_READ = Integer.MAX_VALUE` e `-Xmx128m`:

```
Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
	at java.base/java.util.Arrays.copyOf(Arrays.java:3478)
	at java.base/java.util.ArrayList.grow(ArrayList.java:238)
	at java.base/java.util.ArrayList.add(ArrayList.java:497)
	at java.base/java.lang.String.split(String.java:3512)
	at br.com.zenon.fraud.ingestor.TransactionLineParser.parse(TransactionLineParser.java:72)
	at br.com.zenon.fraud.ingestor.NioTransactionIngestor.read(NioTransactionIngestor.java:60)
	at br.com.zenon.Main.main(Main.java:84)
```

O topo da pilha é o diagnóstico: `ArrayList.grow`. Não faltou memória para **ler** o arquivo — faltou para **guardar** o resultado da leitura. A API de I/O não era o problema; o problema era a assinatura `List<Transaction> read(...)`, que obriga todas as transações a existirem ao mesmo tempo.

### O que foi implementado

| Arquivo | Papel |
|---|---|
| `TransactionReport` | varre o arquivo com `Files.lines` e agrega em uma única passada |
| `TransactionSummary` | record com os três totais: o único resultado que sobrevive à varredura |
| `ReportMain` | ponto de entrada próprio, separado da `Main` |

`Files.lines(Path)` devolve um `Stream<String>` preguiçoso: não lê o arquivo para montar o stream, lê conforme o consumidor pede a próxima linha. Cada linha é contabilizada e descartada na hora.

### Resultado

```
Total de linhas: 6362620
Total de fraudes: 8213
Valor total transacionado: 1144392944759.77
```

Cerca de 2,9 s para os 493 MB.

### Até onde a memória desce

| `-Xmx` | Resultado |
|---|---|
| `128m` (pedido pelo enunciado) | ✅ |
| `32m` | ✅ |
| `16m` | ✅ |
| `8m` | ✅ |

Os mesmos três números, sobre o mesmo arquivo, com **8 MB de heap** — cerca de 1,6% do tamanho do arquivo. O consumo não acompanha o arquivo porque o que ocupa memória é o buffer de leitura, a linha corrente e três acumuladores: um custo constante, independente de o arquivo ter 500 MB ou 500 GB.

### Decisões de implementação

**`BigDecimal` no total, não `double`.** Não é preciosismo, e dá para provar. Uma soma de controle das 6.362.620 parcelas feita em ponto flutuante devolveu `1144392944759.72` — cinco centavos **a menos** que o valor exato. O erro não está em nenhuma parcela isolada: é o acúmulo de milhões de arredondamentos binários. O `BigDecimal` chega no `...759.77` do enunciado porque não arredonda a cada soma.

**O `try`-com-recursos não é opcional.** O stream de `Files.lines` mantém um descritor de arquivo aberto, e fechar o stream é o que o libera. Sem o `try`-com-recursos o descritor vaza até o fim do processo — um defeito que não aparece em teste, porque o programa termina logo depois.

**Redução mutável em vez de `reduce`.** O `collect` de três argumentos — fornecedor, acumulador, combinador — é a forma prevista pela Stream API para acumular em um objeto mutável. Um `reduce` sobre um valor imutável criaria um objeto novo por linha, 6,3 milhões deles sem necessidade. Preferir isso a um `forEach` sobre variável externa também é deliberado: aqui o estado mutável pertence à redução, não ao escopo que a chama.

**Apenas duas colunas são lidas.** O relatório não tem o que fazer com saldos, nomes de clientes ou tipo da operação, então não monta uma `Transaction`.

Vale separar duas coisas que o erro do item 2 costuma confundir: o problema era a **retenção**, não a criação de objetos. Montar e descartar milhões de objetos de vida curta é algo que o coletor de lixo faz bem — construir as 6,3 milhões de `Transaction` e jogá-las fora caberia nos 128 MB. O que não cabe é mantê-las todas referenciadas. Ler só duas colunas economiza trabalho, não memória.

**Uma classe de entrada separada.** A `Main` carrega transações em uma lista para analisá-las e consultá-las, e por isso trabalha sobre uma fatia do arquivo. Juntar as duas na mesma classe apagaria o contraste que a tarefa quer mostrar: a `Main` precisa do limite, a `ReportMain` não.


### Como os números foram verificados

Não há suíte de testes no projeto: o `pom.xml` não declara dependências e `src/test/java` está vazia. Nenhuma das tarefas até aqui pediu testes automatizados.

A conferência foi feita por **oráculo independente**: os três totais foram recalculados varrendo o CSV com `awk`, fora da JVM, e comparados com a saída do programa. Duas implementações que não compartilham uma linha de código chegando ao mesmo número é evidência mais forte do que um teste que afirma um valor copiado da própria saída que ele deveria checar.

Foi essa checagem que expôs o problema do `double`. A primeira soma de controle, feita em ponto flutuante, devolveu `1144392944759.72`; refeita em centavos inteiros, devolveu `1144392944759.77` — o valor do enunciado, e o que o `BigDecimal` produz.

### Execução

O enunciado pede a execução **pela IDE**, com o limite de memória passado como VM Argument.

#### No IntelliJ

`Run → Edit Configurations → + → Application`:

| Campo | Valor |
|---|---|
| Main class | `br.com.zenon.ReportMain` |
| VM options | `-Xmx128m` |
| Working directory | a pasta `zenon-fraud-detector` |

O **working directory** importa: os caminhos dos CSVs são relativos a ele. Com a raiz do repositório selecionada, a leitura falha com `NoSuchFileException`.

Vale marcar *Store as project file*, para que a configuração fique em `.run/` e seja versionada. O padrão do IntelliJ é gravar em `.idea/`, que este repositório ignora (`.gitignore`, linha 35): ali a configuração existiria apenas na máquina de quem a criou.

#### Pelo terminal

```bash
cd zenon-fraud-detector
mvn -q clean compile
java -Xmx128m -cp target/classes br.com.zenon.ReportMain
```

#### Reproduzindo o `OutOfMemoryError` do item 2

Trocar `LAST_LINE_TO_READ` por `Integer.MAX_VALUE` e executar `br.com.zenon.Main` com `-Xmx128m`, pela IDE ou pelo terminal. Devolver o limite a `100_001` em seguida, como o enunciado pede.

Os CSVs ficam em `data/` e não são versionados.

O projeto exige **JDK 25**: as classes de entrada usam o método de instância `void main()` e `IO.println`, finalizados nessa versão.
